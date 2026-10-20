package com.arafawood.api.license;

record LicensePayload(String client, String fingerprint, String issuedAt) {
}
