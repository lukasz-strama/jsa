#ifndef ADC_H
#define ADC_H

#include <stdint.h>

/**
 * @brief Initialize ADC module
 * Channel: 0 (PC0)
 * Reference: AVCC
 * Prescaler: 64
 */
void ADC_Init(void);

/**
 * @brief Start a single ADC conversion
 */
void ADC_StartConversion(void);

#endif
