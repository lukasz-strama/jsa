#include "timer.h"
#include "adc.h"
#include <avr/io.h>
#include <avr/interrupt.h>

// Timer Configuration:
// F_CPU = 16MHz, Prescaler = 64 -> Timer Clock = 250kHz (4us tick)
// 1kHz Target: 1000us / 4us - 1 = 249
// 10kHz Target: 100us / 4us - 1 = 24

void Timer1_Init(void) {
    // CTC Mode (Clear Timer on Compare Match)
    TCCR1B |= (1 << WGM12);
    
    // Enable Output Compare A Match Interrupt
    TIMSK1 |= (1 << OCIE1A);
    
    Timer1_SetFrequency(SAMPLE_RATE_1KHZ);
}

void Timer1_Start(void) {
    // Set Prescaler to 64 to start timer
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

ISR(TIMER1_COMPA_vect) {
    ADC_StartConversion();
}
