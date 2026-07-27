package com.buy01.userservice.service;

/**
 * Temporary class used only to demonstrate SonarQube Quality Gate enforcement.
 *
 * Remove this file after the auditor verifies that Jenkins blocks deployment.
 */
public class QualityGateFailureDemo {

    public int calculateRiskScore(int failedLogins, int suspiciousRequests) {
        int score = 0;

        if (failedLogins > 5) {
            score += 40;
        } else if (failedLogins > 2) {
            score += 20;
        } else {
            score += 5;
        }

        if (suspiciousRequests > 10) {
            score += 50;
        } else if (suspiciousRequests > 5) {
            score += 25;
        } else {
            score += 10;
        }

        return score;
    }

    public String determineRiskLevel(int score) {
        if (score >= 80) {
            return "CRITICAL";
        }

        if (score >= 50) {
            return "HIGH";
        }

        if (score >= 25) {
            return "MEDIUM";
        }

        return "LOW";
    }

    public boolean requiresManualReview(int score, boolean trustedUser) {
        if (trustedUser) {
            return false;
        }

        return score >= 50;
    }
}