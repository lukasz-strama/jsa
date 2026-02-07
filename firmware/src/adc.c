/**
 * @file adc.c
 * @brief ADC driver for ATmega328P — single-channel, interrupt-driven.
 *
 * Configures ADC channel 0 (PC0) with AVCC reference and prescaler /32
 * (500 kHz ADC clock at 16 MHz F_CPU). The ADC ISR encodes each 10-bit
 * sample into the 2-byte sync protocol and transmits it via UART.
 */

#include "adc.h"
#include "uart.h"
#include <avr/io.h>
#include <avr/interrupt.h>

/**
 * @brief Initialise the ADC peripheral.
 *
 * - Reference: AVCC with external capacitor on AREF
 * - Channel: ADC0 (PC0)
 * - Prescaler: /32 → 500 kHz ADC clock → ~26 µs conversion
 * - Interrupt: enabled (ADC_vect)
 */
void ADC_Init(void)
{
    // AVCC with external capacitor at AREF pin
    ADMUX = (1 << REFS0);

    // Enable ADC, Interrupt, and Prescaler 32
    // 16MHz / 32 = 500kHz ADC Clock -> ~26us conversion time
    ADCSRA = (1 << ADEN) | (1 << ADIE) | (1 << ADPS2) | (1 << ADPS0);
}

/**
 * @brief Kick off a single ADC conversion by setting ADSC.
 */
void ADC_StartConversion(void)
{
    ADCSRA |= (1 << ADSC);
}

/**
 * @brief ADC conversion-complete ISR.
 *
 * Reads the 10-bit result, encodes it into two bytes using the sync
 * protocol, and pushes both bytes into the UART TX ring buffer.
 *
 * Protocol encoding:
 * - High byte: @c 1_000_0_D9_D8_D7 (bit 7 = 1 → sync marker)
 * - Low byte:  @c 0_D6_D5_D4_D3_D2_D1_D0 (bit 7 = 0)
 */
ISR(ADC_vect)
{
    // Reading ADC word handles ADCL/ADCH order automatically
    uint16_t adc_val = ADC;

    // Protocol Encoding:
    // High Byte: 1 0 0 0 0 D9 D8 D7 (Sync Bit = 1)
    // Low Byte:  0 D6 D5 D4 D3 D2 D1 D0 (Sync Bit = 0)

    uint8_t high_byte = 0x80 | ((adc_val >> 7) & 0x07);
    uint8_t low_byte = adc_val & 0x7F;

    UART_SendByte(high_byte);
    UART_SendByte(low_byte);
}
