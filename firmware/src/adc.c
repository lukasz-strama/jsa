#include "adc.h"
#include "uart.h"
#include <avr/io.h>
#include <avr/interrupt.h>

void ADC_Init(void) {
    // ADMUX Settings:
    // REFS0 = 1: AVCC with external capacitor at AREF pin
    // MUX3..0 = 0000: ADC0 (PC0)
    ADMUX = (1 << REFS0);

    // ADCSRA Settings:
    // ADEN = 1: Enable ADC
    // ADIE = 1: Enable ADC Interrupt
    // ADPS2, ADPS1 = 1: Prescaler 64
    // 16MHz / 64 = 250kHz ADC Clock
    // Conversion time = 13 cycles / 250kHz = 52us
    ADCSRA = (1 << ADEN) | (1 << ADIE) | (1 << ADPS2) | (1 << ADPS1);
}

void ADC_StartConversion(void) {
    ADCSRA |= (1 << ADSC);
}

// ADC Conversion Complete Interrupt
ISR(ADC_vect) {
    // Read 10-bit ADC value
    // Must read ADCL first, then ADCH (handled automatically by compiler when reading ADC word)
    uint16_t adc_val = ADC; 

    // Protocol Encoding:
    // High Byte: 1 0 0 0 0 D9 D8 D7
    // Low Byte:  0 D6 D5 D4 D3 D2 D1 D0
    
    // Extract bits 9-7 for High Byte and set MSB to 1
    uint8_t high_byte = 0x80 | ((adc_val >> 7) & 0x07);
    
    // Extract bits 6-0 for Low Byte and set MSB to 0
    uint8_t low_byte = adc_val & 0x7F;

    // Push to UART Ring Buffer
    UART_SendByte(high_byte);
    UART_SendByte(low_byte);
}
