#include "adc.h"
#include "uart.h"
#include <avr/io.h>
#include <avr/interrupt.h>

void ADC_Init(void) {
    // AVCC with external capacitor at AREF pin
    ADMUX = (1 << REFS0);

    // Enable ADC, Interrupt, and Prescaler 32
    // 16MHz / 32 = 500kHz ADC Clock -> ~26us conversion time
    ADCSRA = (1 << ADEN) | (1 << ADIE) | (1 << ADPS2) | (1 << ADPS0);
}

void ADC_StartConversion(void) {
    ADCSRA |= (1 << ADSC);
}

ISR(ADC_vect) {
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
