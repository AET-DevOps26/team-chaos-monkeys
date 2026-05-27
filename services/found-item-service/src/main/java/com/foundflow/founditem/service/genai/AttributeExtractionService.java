package com.foundflow.founditem.service.genai;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.founditem.genai.client.model.ExtractAttributesRequest;
import com.foundflow.founditem.genai.client.model.ExtractAttributesResponse;
import com.foundflow.founditem.genai.client.model.ImageContent;
import com.foundflow.photo.storage.PhotoData;
import com.foundflow.photo.storage.PhotoStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Best-effort attribute extraction via the GenAI service (issue #128).
 *
 * <p>The intake critical path must never break because the GenAI service is
 * down, slow, or returning garbage. Every failure here is logged and the
 * service returns {@link Optional#empty()} so the caller can persist the
 * found item without attributes.
 */
@Service
public class AttributeExtractionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttributeExtractionService.class);

    private static final Set<String> GENAI_SUPPORTED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final GenaiClient genaiClient;
    private final PhotoStorage photoStorage;
    private final boolean enabled;

    public AttributeExtractionService(
            GenaiClient genaiClient,
            PhotoStorage photoStorage,
            GenaiProperties properties
    ) {
        this.genaiClient = genaiClient;
        this.photoStorage = photoStorage;
        this.enabled = properties.enabled();
    }

    public Optional<ItemAttributes> extract(String description, String photoKey) {
        if (!enabled) {
            return Optional.empty();
        }

        ExtractAttributesRequest request = buildRequest(description, photoKey);
        if (request == null) {
            return Optional.empty();
        }

        try {
            ExtractAttributesResponse response = genaiClient.extractAttributes(request);
            return Optional.ofNullable(response)
                    .map(ExtractAttributesResponse::getAttributes)
                    .map(AttributeExtractionService::toDomain);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "GenAI attribute extraction failed for photoKey={} — persisting found item without attributes",
                    photoKey,
                    exception
            );
            return Optional.empty();
        }
    }

    private ExtractAttributesRequest buildRequest(String description, String photoKey) {
        ExtractAttributesRequest request = new ExtractAttributesRequest();
        boolean hasContent = false;

        if (description != null && !description.isBlank()) {
            request.setDescription(description);
            hasContent = true;
        }

        ImageContent image = loadImageContent(photoKey);
        if (image != null) {
            request.setImage(image);
            hasContent = true;
        }

        return hasContent ? request : null;
    }

    private ImageContent loadImageContent(String photoKey) {
        if (photoKey == null || photoKey.isBlank()) {
            return null;
        }

        PhotoData photo;
        try {
            photo = photoStorage.retrieve(photoKey);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not load photo {} for GenAI extraction", photoKey, exception);
            return null;
        }

        String contentType = photo.contentType() == null ? null : photo.contentType().toLowerCase();
        if (contentType == null || !GENAI_SUPPORTED_IMAGE_TYPES.contains(contentType)) {
            LOGGER.debug("Skipping image extraction for photoKey={}: unsupported contentType={}", photoKey, photo.contentType());
            closeQuietly(photo.content());
            return null;
        }

        try (InputStream stream = photo.content()) {
            byte[] bytes = stream.readAllBytes();
            ImageContent image = new ImageContent();
            image.setContentType(ImageContent.ContentTypeEnum.fromValue(contentType));
            image.setDataBase64(Base64.getEncoder().encodeToString(bytes));
            return image;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not read photo {} for GenAI extraction", photoKey, exception);
            return null;
        }
    }

    private static void closeQuietly(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    private static ItemAttributes toDomain(
            com.foundflow.founditem.genai.client.model.ItemAttributes attrs
    ) {
        List<String> marks = attrs.getDistinguishingMarks() == null
                ? List.of()
                : List.copyOf(attrs.getDistinguishingMarks());
        return new ItemAttributes(
                attrs.getCategory(),
                attrs.getBrand(),
                attrs.getColor(),
                marks
        );
    }
}
