#include "timer.h"
#include "adc.h"
#include <avr/io.h>
#include <avr/interrupt.h>

// Timer Configuration Calculations:
// F_CPU = 16MHz
// Prescaler = 64
// Timer Clock = 16MHz / 64 = 250kHz
// Tick Duration = 1 / 250kHz = 4us

// Target: 1kHz (1000us period)
// OCR1A = (1000us / 4us) - 1 = 249

// Target: 10kHz (100us period)
// OCR1A = (100us / 4us) - 1 = 24

void Timer1_Init(void) {
    // TCCR1B Settings:
    // WGM12 = 1: CTC Mode (Clear Timer on Compare Match)
    TCCR1B |= (1 << WGM12);
    
    // TIMSK1 Settings:
    // OCIE1A = 1: Enable Output Compare A Match Interrupt
    TIMSK1 |= (1 << OCIE1A);
    
    // Set default frequency to 1kHz
    Timer1_SetFrequency(SAMPLE_RATE_1KHZ);
}

void Timer1_Start(void) {
    // Set Prescaler to 64 (CS11 and CS10)
    TCCR1B |= (1 << CS11) | (1 << CS10);
    TCCR1B &= ~(1 << CS12);
}

void Timer1_Stop(void) {
    // Clear Clock Select bits to stop timer
    TCCR1B &= ~((1 << CS12) | (1 << CS11) | (1 << CS10));
}

void Timer1_SetFrequency(SampleRate rate) {
    if (rate == SAMPLE_RATE_1KHZ) {
        OCR1A = 249;
    } else if (rate == SAMPLE_RATE_10KHZ) {
        OCR1A = 24;
    }
}

// Timer1 Compare Match A Interrupt
ISR(TIMER1_COMPA_vect) {
    // Trigger ADC Conversion
    ADC_StartConversion();
}
