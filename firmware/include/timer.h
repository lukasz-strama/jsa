/**
 * @file timer.h
 * @brief Timer1 driver interface — sample-rate generation for ADC triggering.
 */

#ifndef TIMER_H
#define TIMER_H

#include <stdint.h>

/**
 * @brief Supported sample-rate presets.
 */
typedef enum
{
    SAMPLE_RATE_1KHZ,  /**< 1 kHz sampling rate. */
    SAMPLE_RATE_10KHZ, /**< 10 kHz sampling rate. */
    SAMPLE_RATE_20KHZ  /**< 20 kHz sampling rate (Turbo Mode). */
} SampleRate;

/**
 * @brief Initialize Timer1 in CTC mode
 */
void Timer1_Init(void);

/**
 * @brief Start Timer1 (Enable Clock)
 */
void Timer1_Start(void);

/**
 * @brief Stop Timer1 (Disable Clock)
 */
void Timer1_Stop(void);

/**
 * @brief Set sampling frequency
 * @param rate SAMPLE_RATE_1KHZ, SAMPLE_RATE_10KHZ, or SAMPLE_RATE_20KHZ
 */
void Timer1_SetFrequency(SampleRate rate);

#endif
