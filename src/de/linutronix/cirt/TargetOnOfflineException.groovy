#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * CI-RT library libvirt Exception
 */

package de.linutronix.cirt;

class TargetOnOfflineException extends RuntimeException {
        TargetOnOfflineException (String message) {
                super(message);
        }
}
