/**
 * @file gpio.h
 * @brief GPIO initialisation and LED helper interface.
 */

#ifndef GPIO_H
#define GPIO_H

#include <avr/io.h>

/**
 * @brief Initialize GPIO pins for safety and functionality.
 * - Configures unused pins as INPUT_PULLUP to prevent floating inputs.
 * - Configures LED pin (PB5) as Output.
 * - Skips specific pins used by peripherals (ADC, UART, XTAL, RESET).
 */
void GPIO_InitSafe(void);

/**
 * @brief Toggle the built-in LED (PB5).
 * Used to indicate errors like buffer overflow.
 */
void GPIO_ToggleLED(void);

#endif
