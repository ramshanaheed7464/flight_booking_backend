package com.example.flight_booking_backend.email;

import static com.example.flight_booking_backend.email.EmailPalette.*;

public final class EmailComponents {

    private EmailComponents() {
    }

    public static String wrapper(String content) {
        return "<div style='background:" + BLACK + ";font-family:Georgia,serif;" +
                "max-width:640px;margin:auto;border-radius:12px;overflow:hidden;" +
                "border:1px solid " + BORDER + ";'>" +
                content +
                "</div>";
    }

    public static String header(String title, String subtitle) {
        return "<div style='background:" + BLACK + ";padding:40px 32px 32px;text-align:center;" +
                "border-bottom:1px solid " + BORDER + ";'>" +
                "<div style='font-size:48px;margin-bottom:12px;'>" + IC_PLANE + "</div>" +
                "<h1 style='margin:0 0 6px;font-family:Georgia,serif;font-size:28px;" +
                "font-weight:700;color:" + GOLD + ";letter-spacing:0.5px;'>" + title + "</h1>" +
                "<p style='margin:0;font-size:13px;color:" + MUTED + ";" +
                "letter-spacing:1px;text-transform:uppercase;'>" + subtitle + "</p>" +
                "<div style='width:60px;height:2px;background:" + GOLD +
                ";margin:18px auto 0;border-radius:2px;'></div>" +
                "</div>";
    }

    public static String greeting(String userName, String bodyMessage) {
        return "<div style='padding:28px 32px 4px;'>" +
                "<p style='font-size:16px;margin:0 0 6px;color:" + WHITE + ";'>Dear " +
                "<span style='color:" + GOLD + ";font-weight:700;'>" + userName + "</span>,</p>" +
                "<p style='color:" + MUTED + ";margin:0;font-size:14px;line-height:1.7;'>" +
                bodyMessage + "</p>" +
                "</div>";
    }

    public static String sectionTitle(String title) {
        return "<h3 style='color:" + GOLD + ";font-family:Georgia,serif;font-size:13px;" +
                "font-weight:700;text-transform:uppercase;letter-spacing:1.5px;" +
                "border-bottom:1px solid " + BORDER + ";padding-bottom:10px;margin-bottom:0;'>" +
                title + "</h3>";
    }

    public static String row(String icon, String label, String value, boolean alt) {
        String bg = alt ? CARD : DARK;
        return "<tr style='background:" + bg + ";'>" +
                "<td style='padding:11px 14px;width:40%;border-bottom:1px solid " + BORDER + ";'>" +
                "<span style='color:" + MUTED + ";font-size:13px;'>" +
                "<span style='margin-right:8px;font-size:15px;'>" + icon + "</span>" + label +
                "</span></td>" +
                "<td style='padding:11px 14px;color:" + WHITE + ";font-size:13px;" +
                "border-bottom:1px solid " + BORDER + ";'>" +
                (value != null ? value : "N/A") + "</td></tr>";
    }

    public static String miniRow(String icon, String label, String value) {
        return "<tr>" +
                "<td style='padding:6px 8px;color:" + MUTED + ";width:42%;'>" +
                "<span style='margin-right:6px;'>" + icon + "</span>" + label +
                "</td>" +
                "<td style='padding:6px 8px;color:" + WHITE + ";'>" +
                (value != null && !value.isBlank() ? value : "&#8212;") + "</td></tr>";
    }

    public static String flightRows(
            com.example.flight_booking_backend.model.Flight f,
            String depStr,
            String arrStr) {
        return row(IC_PLANE, "Flight Number", f.getFlightNumber(), true) +
                row(IC_FROM, "From", f.getSource(), false) +
                row(IC_TO, "To", f.getDestination(), true) +
                row(IC_CLOCK, "Departure", depStr, false) +
                row(IC_CLOCK, "Arrival", arrStr, true);
    }

    public static String footer() {
        return "<div style='padding:28px 32px;text-align:center;" +
                "border-top:1px solid " + BORDER + ";margin-top:24px;'>" +
                "<div style='font-size:24px;margin-bottom:10px;'>" + IC_GLOBE2 + "</div>" +
                "<p style='margin:0 0 4px;color:" + GOLD + ";font-size:13px;" +
                "font-weight:700;letter-spacing:0.5px;'>Thank you for flying with us!</p>" +
                "<p style='margin:0;color:" + MUTED + ";font-size:12px;'>" +
                "This is an automated email &mdash; please do not reply directly.</p>" +
                "</div>";
    }

    public static String priceValue(double pkrAmount) {
        return "<span style='color:" + GOLD + ";font-weight:700;font-size:16px;'>PKR " +
                String.format("%,.2f", pkrAmount) + "</span>";
    }
}