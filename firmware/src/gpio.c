/**
 * @file gpio.c
 * @brief GPIO initialisation and LED helper for ATmega328P.
 *
 * Configures all unused pins as INPUT_PULLUP to avoid floating inputs
 * and sets PB5 (built-in LED) as output for error indication.
 */

#include "gpio.h"

/**
 * @brief Initialise all GPIO ports for safe operation.
 *
 * - PORT B: PB0-PB4 → input pull-up; PB5 → output (LED); PB6-PB7 → XTAL
 * - PORT C: PC0 → ADC (Hi-Z); PC1-PC5 → input pull-up; PC6 → RESET
 * - PORT D: PD0-PD1 → UART; PD2-PD7 → input pull-up
 */
void GPIO_InitSafe(void)
{
    // --- PORT B ---
    // PB0-PB4: Unused -> Input Pullup
    // PB5: Built-in LED -> Output Low (initially)
    // PB6-PB7: XTAL -> Leave untouched (External Clock)

    DDRB |= (1 << PB5);   // Set PB5 as Output
    PORTB &= ~(1 << PB5); // Set Low

    // Enable Pullups for unused pins
    PORTB |= (1 << PB0) | (1 << PB1) | (1 << PB2) | (1 << PB3) | (1 << PB4);

    // --- PORT C ---
    // PC0: ADC Input -> Input, No Pullup (High-Z)
    // PC1-PC5: Unused -> Input Pullup
    // PC6: RESET -> Leave untouched

    // DDRC is 0x00 by default (Input)
    // Enable Pullups for unused pins (Skip PC0 for ADC)
    PORTC |= (1 << PC1) | (1 << PC2) | (1 << PC3) | (1 << PC4) | (1 << PC5);

    // --- PORT D ---
    // PD0 (RX): UART -> Handled by UART module
    // PD1 (TX): UART -> Handled by UART module
    // PD2-PD7: Unused -> Input Pullup

    // Enable Pullups for unused pins
    PORTD |= (1 << PD2) | (1 << PD3) | (1 << PD4) | (1 << PD5) | (1 << PD6) | (1 << PD7);
}

/**
 * @brief Toggle the built-in LED (PB5).
 *
 * Used as a visual indicator for error conditions (e.g. TX buffer overflow).
 */
void GPIO_ToggleLED(void)
{
    PORTB ^= (1 << PB5);
}
