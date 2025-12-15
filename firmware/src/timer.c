#include "timer.h"
#include "adc.h"
#include <avr/io.h>
#include <avr/interrupt.h>

// Timer Configuration:
// F_CPU = 16MHz, Prescaler = 1 -> Timer Clock = 16MHz (0.0625us tick)
// 1kHz Target: 16000000 / 1000 - 1 = 15999
// 10kHz Target: 16000000 / 10000 - 1 = 1599
// 20kHz Target: 16000000 / 20000 - 1 = 799

void Timer1_Init(void) {
    // CTC Mode (Clear Timer on Compare Match)
    TCCR1B |= (1 << WGM12);
    
    // Enable Output Compare A Match Interrupt
    TIMSK1 |= (1 << OCIE1A);
    
    Timer1_SetFrequency(SAMPLE_RATE_1KHZ);
}

void Timer1_Start(void) {
    // Set Prescaler to 1 to start timer
    TCCR1B |= (1 << CS10);
    TCCR1B &= ~((1 << CS12) | (1 << CS11));
}

void Timer1_Stop(void) {
    // Clear Clock Select bits to stop timer
    TCCR1B &= ~((1 << CS12) | (1 << CS11) | (1 << CS10));
}

void Timer1_SetFrequency(SampleRate rate) {
    if (rate == SAMPLE_RATE_1KHZ) {
        OCR1A = 15999;
    } else if (rate == SAMPLE_RATE_10KHZ) {
        OCR1A = 1599;
    } else if (rate == SAMPLE_RATE_20KHZ) {
        OCR1A = 799;
    }
}

ISR(TIMER1_COMPA_vect) {
    ADC_StartConversion();
}
