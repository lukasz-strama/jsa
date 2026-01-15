#ifndef TIMER_H
#define TIMER_H

#include <stdint.h>

typedef enum {
    SAMPLE_RATE_1KHZ,
    SAMPLE_RATE_10KHZ,
    SAMPLE_RATE_20KHZ
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
