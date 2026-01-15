#ifndef ADC_H
#define ADC_H

#include <stdint.h>

/**
 * @brief Initialize ADC module
 * Channel: 0 (PC0)
 * Reference: AVCC
 * Prescaler: 32 (500kHz ADC clock @ 16MHz F_CPU)
 * 
 * Note: ADC ISR sends samples via UART using 2-byte protocol.
 */
void ADC_Init(void);

/**
 * @brief Start a single ADC conversion
 */
void ADC_StartConversion(void);

#endif
