/**
 * @file timer.c
 * @brief Timer1 driver — generates a periodic ADC trigger at the
 *        configured sample rate.
 *
 * Timer1 runs in CTC mode with prescaler /1 (16 MHz tick).
 * Compare-match values:
 * - 1 kHz:  OCR1A = 15 999
 * - 10 kHz: OCR1A = 1 599
 * - 20 kHz: OCR1A = 799
 */

#include "timer.h"
#include "adc.h"
#include <avr/io.h>
#include <avr/interrupt.h>

// Timer Configuration:
// F_CPU = 16MHz, Prescaler = 1 -> Timer Clock = 16MHz (0.0625us tick)
// 1kHz Target: 16000000 / 1000 - 1 = 15999
// 10kHz Target: 16000000 / 10000 - 1 = 1599
// 20kHz Target: 16000000 / 20000 - 1 = 799

/**
 * @brief Initialise Timer1 in CTC (Clear Timer on Compare Match) mode
 *        and enable the compare-match interrupt.
 *
 * Default rate: 1 kHz.
 */
void Timer1_Init(void)
{
    // CTC Mode (Clear Timer on Compare Match)
    TCCR1B |= (1 << WGM12);

    // Enable Output Compare A Match Interrupt
    TIMSK1 |= (1 << OCIE1A);

    Timer1_SetFrequency(SAMPLE_RATE_1KHZ);
}

/**
 * @brief Start Timer1 by selecting prescaler = 1 (CS10).
 */
void Timer1_Start(void)
{
    // Set Prescaler to 1 to start timer
    TCCR1B |= (1 << CS10);
    TCCR1B &= ~((1 << CS12) | (1 << CS11));
}

/**
 * @brief Stop Timer1 by clearing all clock-select bits.
 */
void Timer1_Stop(void)
{
    // Clear Clock Select bits to stop timer
    TCCR1B &= ~((1 << CS12) | (1 << CS11) | (1 << CS10));
}

/**
 * @brief Set the sampling frequency by updating OCR1A.
 */
void Timer1_SetFrequency(SampleRate rate)
{
    if (rate == SAMPLE_RATE_1KHZ)
    {
        OCR1A = 15999;
    }
    else if (rate == SAMPLE_RATE_10KHZ)
    {
        OCR1A = 1599;
    }
    else if (rate == SAMPLE_RATE_20KHZ)
    {
        OCR1A = 799;
    }
}

/**
 * @brief Timer1 compare-match A ISR — triggers one ADC conversion.
 */
ISR(TIMER1_COMPA_vect)
{
    ADC_StartConversion();
}
