#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * CI-RT library: Variable not set exception
 */

package de.linutronix.cirt;

class VarNotSetException extends RuntimeException {
        VarNotSetException (String name) {
                super("variable ${name} is not set");
        }
}
