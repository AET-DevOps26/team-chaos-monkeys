package com.foundflow.genai.client;

import com.foundflow.common.domain.ItemAttributes;

/**
 * Result of a GenAI attribute extraction: the structured {@link ItemAttributes}
 * plus the free-text location the model recovered from the intake, if any.
 *
 * <p>Location is kept separate from {@code ItemAttributes} because it is a
 * property of the item itself — consumers map it onto their own location
 * column (e.g. {@code FoundItem.location}), not onto the attributes.
 */
public record ExtractionResult(ItemAttributes attributes, String location) {
}
