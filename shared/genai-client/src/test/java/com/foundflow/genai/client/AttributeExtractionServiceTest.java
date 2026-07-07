package com.foundflow.genai.client;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.genai.client.model.ExtractAttributesRequest;
import com.foundflow.genai.client.model.ExtractAttributesResponse;
import com.foundflow.genai.client.model.ImageContent;
import com.foundflow.photo.storage.PhotoData;
import com.foundflow.photo.storage.PhotoStorage;
import com.foundflow.photo.storage.PhotoStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributeExtractionServiceTest {

    @Mock
    private GenaiClient genaiClient;

    @Mock
    private PhotoStorage photoStorage;

    private final GenaiProperties enabled = new GenaiProperties(
            "http://genai", Duration.ofSeconds(1), Duration.ofSeconds(1), null, true
    );

    @Test
    void returnsEmpty_whenDisabled() {
        GenaiProperties disabled = new GenaiProperties(
                "http://genai", Duration.ofSeconds(1), Duration.ofSeconds(1), null, false
        );
        AttributeExtractionService service = new AttributeExtractionService(genaiClient, photoStorage, disabled);

        Optional<ItemAttributes> result = service.extract("a description", "photo-key");

        assertTrue(result.isEmpty());
        verifyNoInteractions(genaiClient, photoStorage);
    }

    @Test
    void returnsEmpty_whenNoDescriptionAndNoPhoto() {
        AttributeExtractionService service = new AttributeExtractionService(genaiClient, photoStorage, enabled);

        Optional<ItemAttributes> result = service.extract(null, null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(genaiClient, photoStorage);
    }

    @Test
    void sendsTextOnly_whenPhotoMissing() {
        AttributeExtractionService service = new AttributeExtractionService(genaiClient, photoStorage, enabled);

        ExtractAttributesResponse response = buildResponse("wallet", "Nike", "red");
        when(genaiClient.extractAttributes(any())).thenReturn(response);

        Optional<ItemAttributes> result = service.extract("Lost a red Nike wallet", null);

        ArgumentCaptor<ExtractAttributesRequest> captor = ArgumentCaptor.forClass(ExtractAttributesRequest.class);
        verify(genaiClient).extractAttributes(captor.capture());
        assertEquals("Lost a red Nike wallet", captor.getValue().getDescription());
        assertNull(captor.getValue().getImage());

        assertTrue(result.isPresent());
        assertEquals("wallet", result.get().getCategory());
        verify(photoStorage, never()).retrieve(any());
    }

    @Test
    void sendsBase64Image_whenPhotoPresent() {
        AttributeExtractionService service = new AttributeExtractionService(genaiClient, photoStorage, enabled);

        byte[] photoBytes = "PNG-CONTENT".getBytes();
        when(photoStorage.retrieve("photo-key")).thenReturn(new PhotoData(
                new ByteArrayInputStream(photoBytes), "image/png", photoBytes.length
        ));
        when(genaiClient.extractAttributes(any())).thenReturn(buildResponse("jacket", null, "black"));

        Optional<ItemAttributes> result = service.extract(null, "photo-key");

        ArgumentCaptor<ExtractAttributesRequest> captor = ArgumentCaptor.forClass(ExtractAttributesRequest.class);
        verify(genaiClient).extractAttributes(captor.capture());
        ImageContent image = captor.getValue().getImage();
        assertNotNull(image);
        assertEquals(ImageContent.ContentTypeEnum.PNG, image.getContentType());
        assertTrue(image.getDataBase64() != null && !image.getDataBase64().isEmpty());

        assertTrue(result.isPresent());
        assertEquals("jacket", result.get().getCategory());
    }

    @Test
    void downscalesOversizedImage_beforeSendingToGenai() throws Exception {
        AttributeExtractionService service = new AttributeExtractionService(genaiClient, photoStorage, enabled);

        byte[] oversizedPng = noisePng(1500, 1500);
        assertTrue(oversizedPng.length > AttributeExtractionService.MAX_IMAGE_BYTES,
                "test image must exceed the GenAI byte limit");
        when(photoStorage.retrieve("photo-key")).thenReturn(new PhotoData(
                new ByteArrayInputStream(oversizedPng), "image/png", oversizedPng.length
        ));
        when(genaiClient.extractAttributes(any())).thenReturn(buildResponse("jacket", null, "black"));

        Optional<ItemAttributes> result = service.extract(null, "photo-key");

        ArgumentCaptor<ExtractAttributesRequest> captor = ArgumentCaptor.forClass(ExtractAttributesRequest.class);
        verify(genaiClient).extractAttributes(captor.capture());
        ImageContent image = captor.getValue().getImage();
        assertNotNull(image);
        assertEquals(ImageContent.ContentTypeEnum.JPEG, image.getContentType());
        assertTrue(image.getDataBase64().length() <= 6_700_000,
                "payload must fit the GenAI schema limit");
        BufferedImage sent = ImageIO.read(new ByteArrayInputStream(
                Base64.getDecoder().decode(image.getDataBase64())));
        assertNotNull(sent);
        assertTrue(Math.max(sent.getWidth(), sent.getHeight()) <= 1280);

        assertTrue(result.isPresent());
    }

    @Test
    void dropsOversizedImage_whenUndecodable_butStillExtractsFromText() {
        AttributeExtractionService service = new AttributeExtractionService(genaiClient, photoStorage, enabled);

        // WebP passes the content-type filter but the JDK ImageIO cannot decode it.
        byte[] junk = new byte[AttributeExtractionService.MAX_IMAGE_BYTES + 1];
        when(photoStorage.retrieve("photo-key")).thenReturn(new PhotoData(
                new ByteArrayInputStream(junk), "image/webp", junk.length
        ));
        when(genaiClient.extractAttributes(any())).thenReturn(buildResponse("hat", null, null));

        service.extract("text desc", "photo-key");

        ArgumentCaptor<ExtractAttributesRequest> captor = ArgumentCaptor.forClass(ExtractAttributesRequest.class);
        verify(genaiClient).extractAttributes(captor.capture());
        assertNull(captor.getValue().getImage());
        assertEquals("text desc", captor.getValue().getDescription());
    }

    @Test
    void skipsImage_whenContentTypeUnsupported() {
        AttributeExtractionService service = new AttributeExtractionService(genaiClient, photoStorage, enabled);

        when(photoStorage.retrieve("photo-key")).thenReturn(new PhotoData(
                new ByteArrayInputStream("HEIC".getBytes()), "image/heic", 4
        ));
        when(genaiClient.extractAttributes(any())).thenReturn(buildResponse("hat", null, null));

        service.extract("text desc", "photo-key");

        ArgumentCaptor<ExtractAttributesRequest> captor = ArgumentCaptor.forClass(ExtractAttributesRequest.class);
        verify(genaiClient).extractAttributes(captor.capture());
        assertNull(captor.getValue().getImage());
    }

    @Test
    void mapsLocation_whenPresentInExtraction() {
        AttributeExtractionService service = new AttributeExtractionService(genaiClient, photoStorage, enabled);

        ExtractAttributesResponse response = buildResponse("backpack", null, "black");
        response.getAttributes().setLocation("neben Buehne 2");
        when(genaiClient.extractAttributes(any())).thenReturn(response);

        Optional<ExtractionResult> result = service.extractWithLocation("Schwarzer Rucksack neben Buehne 2", null);

        assertTrue(result.isPresent());
        assertEquals("neben Buehne 2", result.get().location());
        assertEquals("backpack", result.get().attributes().getCategory());
    }

    @Test
    void mapsNullLocation_whenAbsentFromExtraction() {
        AttributeExtractionService service = new AttributeExtractionService(genaiClient, photoStorage, enabled);

        when(genaiClient.extractAttributes(any())).thenReturn(buildResponse("wallet", null, "brown"));

        Optional<ExtractionResult> result = service.extractWithLocation("Found a brown wallet", null);

        assertTrue(result.isPresent());
        assertNull(result.get().location());
    }

    @Test
    void returnsEmpty_whenClientThrows() {
        AttributeExtractionService service = new AttributeExtractionService(genaiClient, photoStorage, enabled);

        when(genaiClient.extractAttributes(any()))
                .thenThrow(new RestClientException("genai-service unreachable"));

        Optional<ItemAttributes> result = service.extract("test", null);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmpty_whenPhotoStorageThrows() {
        AttributeExtractionService service = new AttributeExtractionService(genaiClient, photoStorage, enabled);

        when(photoStorage.retrieve("photo-key"))
                .thenThrow(new PhotoStorageException("backend down"));

        Optional<ItemAttributes> result = service.extract(null, "photo-key");

        assertTrue(result.isEmpty());
        verifyNoInteractions(genaiClient);
    }

    /** Random-noise PNG — incompressible, so pixel count translates directly into bytes. */
    private static byte[] noisePng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private ExtractAttributesResponse buildResponse(String category, String brand, String color) {
        com.foundflow.genai.client.model.ItemAttributes attrs =
                new com.foundflow.genai.client.model.ItemAttributes();
        attrs.setCategory(category);
        attrs.setBrand(brand);
        attrs.setColor(color);
        attrs.setDistinguishingMarks(List.of());
        ExtractAttributesResponse response = new ExtractAttributesResponse();
        response.setAttributes(attrs);
        return response;
    }
}
