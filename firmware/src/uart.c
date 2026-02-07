/**
 * @file uart.c
 * @brief UART driver for ATmega328P — 2 Mbps, 8N1, interrupt-driven TX.
 *
 * Uses a 256-byte ring buffer for non-blocking transmission.
 * The UDRE interrupt drains the buffer one byte at a time;
 * if the buffer overflows, data is dropped and the LED is toggled.
 */

#include "uart.h"
#include "gpio.h"
#include <avr/io.h>
#include <avr/interrupt.h>

/** TX ring-buffer size (must be a power of two). */
#define TX_BUFFER_SIZE 256

/** Bitmask for wrapping the ring-buffer index. */
#define TX_BUFFER_MASK (TX_BUFFER_SIZE - 1)

/** TX ring buffer. */
volatile uint8_t tx_buffer[TX_BUFFER_SIZE];

/** Write-head index into the TX ring buffer. */
volatile uint8_t tx_head = 0;

/** Read-tail index into the TX ring buffer. */
volatile uint8_t tx_tail = 0;

/**
 * @brief Initialise UART0 for 2 Mbps, 8N1.
 *
 * At F_CPU = 16 MHz with U2X0 enabled:
 * Baud = F_CPU / (8 × (UBRR + 1)) = 16 000 000 / (8 × 1) = 2 000 000.
 */
void UART_Init(void)
{
    // Configure for 2 Mbps (2,000,000 baud)
    // F_CPU = 16MHz
    // Formula: Baud = F_CPU / (8 * (UBRR + 1))
    // 2,000,000 = 16,000,000 / (8 * (0 + 1)) -> Exact match with UBRR=0

    UBRR0H = 0;
    UBRR0L = 0;

    // Enable Double Speed Mode (U2X0) is REQUIRED for 2Mbps
    UCSR0A |= (1 << U2X0);

    // Enable Receiver and Transmitter
    UCSR0B = (1 << RXEN0) | (1 << TXEN0);

    // Frame format: 8 data bits, 1 stop bit, No parity
    UCSR0C = (1 << UCSZ01) | (1 << UCSZ00);
}

/**
 * @brief Enqueue a byte for transmission via the TX ring buffer.
 *
 * If the buffer is full the byte is silently dropped and the LED is
 * toggled to signal the overflow.
 */
void UART_SendByte(uint8_t data)
{
    uint8_t next_head = (tx_head + 1) & TX_BUFFER_MASK;

    if (next_head != tx_tail)
    {
        tx_buffer[tx_head] = data;
        tx_head = next_head;

        // Enable Data Register Empty Interrupt to start transmission
        UCSR0B |= (1 << UDRIE0);
    }
    else
    {
        // Buffer Overflow: Drop data and indicate error via LED
        GPIO_ToggleLED();
    }
}

/**
 * @brief Blocking receive — waits until a byte is available in UDR0.
 *
 * @return the received byte
 */
uint8_t UART_ReceiveByte(void)
{
    // Blocking wait for data
    while (!(UCSR0A & (1 << RXC0)))
        ;
    return UDR0;
}

/**
 * @brief Check whether the UART RX buffer contains data.
 *
 * @return 1 if at least one byte is available, 0 otherwise
 */
uint8_t UART_IsDataAvailable(void)
{
    return (UCSR0A & (1 << RXC0));
}

/**
 * @brief USART Data Register Empty ISR — transmits the next byte from
 *        the ring buffer, or disables the interrupt when empty.
 */
ISR(USART_UDRE_vect)
{
    if (tx_head != tx_tail)
    {
        UDR0 = tx_buffer[tx_tail];
        tx_tail = (tx_tail + 1) & TX_BUFFER_MASK;
    }
    else
    {
        // Buffer empty, disable interrupt to prevent infinite ISR calls
        UCSR0B &= ~(1 << UDRIE0);
    }
}
