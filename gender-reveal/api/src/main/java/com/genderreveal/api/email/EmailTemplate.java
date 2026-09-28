package com.genderreveal.api.email;

/** Wraps plain-text email copy in a small branded HTML shell matching the web app's design tokens. */
public final class EmailTemplate {

    private EmailTemplate() {}

    public static String render(String heading, String bodyHtml) {
        return """
            <!doctype html>
            <html lang="ko">
              <head><meta charset="utf-8"></head>
              <body style="margin:0;padding:32px 16px;background-color:#FFFBF6;
                font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#2E2B27;">
                <div style="max-width:480px;margin:0 auto;background-color:#FFFFFF;
                  border:1px solid #E6E0D5;border-radius:16px;padding:32px;text-align:center;">
                  <p style="margin:0 0 16px;font-size:13px;font-weight:600;color:#F2793A;">젠더리빌</p>
                  <h1 style="margin:0 0 16px;font-size:20px;font-weight:600;line-height:28px;">%s</h1>
                  %s
                </div>
              </body>
            </html>
            """.formatted(heading, bodyHtml);
    }

    public static String paragraph(String text) {
        return "<p style=\"margin:0 0 16px;font-size:14px;line-height:22px;text-align:center;\">" + escape(text) + "</p>";
    }

    public static String mutedParagraph(String text) {
        return "<p style=\"margin:16px 0 0;font-size:12px;line-height:18px;color:#8C8579;text-align:center;\">"
            + escape(text) + "</p>";
    }

    public static String button(String href, String label) {
        return "<a href=\"" + escapeAttribute(href) + "\" style=\"display:inline-block;padding:12px 24px;"
            + "border-radius:999px;background-color:#F2793A;color:#FFFFFF;text-decoration:none;"
            + "font-size:14px;font-weight:600;\">" + escape(label) + "</a>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttribute(String text) {
        return escape(text).replace("\"", "&quot;");
    }
}
