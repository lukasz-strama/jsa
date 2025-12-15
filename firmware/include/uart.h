#ifndef UART_H
#define UART_H

#include <stdint.h>

#define UART_BAUD 2000000

/**
 * @brief Initialize UART module
 * Baud Rate: 2,000,000 (2 Mbps)
 * Format: 8N1
 */
void UART_Init(void);

/**
 * @brief Send a byte via UART (Non-blocking / Buffered)
 * Adds byte to ring buffer and enables UDRE interrupt.
 * @param data Byte to send
 */
void UART_SendByte(uint8_t data);

/**
 * @brief Receive a byte (Blocking)
 * @return Received byte
 */
uint8_t UART_ReceiveByte(void);

/**
 * @brief Check if data is available in RX buffer
 * @return 1 if data available, 0 otherwise
 */
uint8_t UART_IsDataAvailable(void);

#endif
