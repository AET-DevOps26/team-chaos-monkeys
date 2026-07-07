package com.foundflow.notification.service;

/**
 * Renders the shared FoundFlow email layout as an inline-CSS HTML string.
 * No templating engine by design: one text block, values HTML-escaped.
 */
final class EmailHtml {

    private static final String BRAND_COLOR = "#7D33FF";

    // %s slots: body paragraph (escaped), CTA block (pre-rendered HTML or empty).
    private static final String LAYOUT = """
            <div style="margin:0;padding:24px;background-color:#f4f4f7;font-family:Arial,Helvetica,sans-serif;">
              <div style="max-width:520px;margin:0 auto;background-color:#ffffff;border-radius:8px;overflow:hidden;">
                <div style="background-color:%s;padding:20px 32px;">
                  <span style="color:#ffffff;font-size:22px;font-weight:bold;letter-spacing:0.5px;">FoundFlow</span>
                </div>
                <div style="padding:32px;">
                  <p style="margin:0 0 16px 0;color:#333333;font-size:15px;line-height:1.6;">%s</p>
                  %s
                </div>
              </div>
            </div>
            """;

    private static final String CTA = """
            <p style="text-align:center;margin:24px 0 8px 0;">
              <a href="%s" style="display:inline-block;background-color:%s;color:#ffffff;padding:12px 28px;border-radius:6px;text-decoration:none;font-size:15px;font-weight:bold;">%s</a>
            </p>
            <p style="margin:16px 0 0 0;color:#8a8a8a;font-size:12px;line-height:1.5;word-break:break-all;">If the button does not work, copy this link into your browser:<br>%s</p>
            """;

    private EmailHtml() {
    }

    /** Renders the layout without a CTA button. */
    static String render(String bodyText) {
        return LAYOUT.formatted(BRAND_COLOR, escape(bodyText), "");
    }

    /** Renders the layout with a CTA button; the URL is also printed as plain text below it. */
    static String render(String bodyText, String ctaLabel, String ctaUrl) {
        String escapedUrl = escape(ctaUrl);
        String cta = CTA.formatted(escapedUrl, BRAND_COLOR, escape(ctaLabel), escapedUrl);
        return LAYOUT.formatted(BRAND_COLOR, escape(bodyText), cta);
    }

    /** Minimal HTML escaping for untrusted values interpolated into markup and href attributes. */
    static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
