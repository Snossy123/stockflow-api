package com.arafawood.api.license;

public record LicenseView(boolean enforced, boolean valid, String client, String message) {
}
