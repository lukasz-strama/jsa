#include "uart.h"
#include <avr/io.h>
#include <avr/interrupt.h>

#define BAUD 115200
#define F_CPU 16000000UL
#define UBRR_VALUE ((F_CPU/8/BAUD)-1)

// Ring Buffer Configuration
#define TX_BUFFER_SIZE 256
#define TX_BUFFER_MASK (TX_BUFFER_SIZE - 1)

volatile uint8_t tx_buffer[TX_BUFFER_SIZE];
volatile uint8_t tx_head = 0;
volatile uint8_t tx_tail = 0;

void UART_Init(void) {
    // Set baud rate
    // UBRR0H contains the 4 most significant bits
    UBRR0H = (uint8_t)(UBRR_VALUE >> 8);
    // UBRR0L contains the 8 least significant bits
    UBRR0L = (uint8_t)UBRR_VALUE;

    // Enable Double Speed Mode (U2X0) for better baud rate accuracy at 115200
    UCSR0A |= (1 << U2X0);

    // Enable receiver and transmitter
    // RXEN0: Enable Receiver
    // TXEN0: Enable Transmitter
    UCSR0B = (1 << RXEN0) | (1 << TXEN0);

    // Set frame format: 8 data bits, 1 stop bit, No parity
    // UCSZ01, UCSZ00: 8-bit character size
    UCSR0C = (1 << UCSZ01) | (1 << UCSZ00);
}

void UART_SendByte(uint8_t data) {
    uint8_t next_head = (tx_head + 1) & TX_BUFFER_MASK;

    // Check if buffer is full
    if (next_head != tx_tail) {
        tx_buffer[tx_head] = data;
        tx_head = next_head;
        
        // Enable Data Register Empty Interrupt to start transmission
        UCSR0B |= (1 << UDRIE0);
    }
    // If buffer is full, data is dropped (Real-time constraint)
}

uint8_t UART_ReceiveByte(void) {
    // Wait for data to be received
    while (!(UCSR0A & (1 << RXC0)));
    return UDR0;
}

uint8_t UART_IsDataAvailable(void) {
    return (UCSR0A & (1 << RXC0));
}

// Interrupt Service Routine for Data Register Empty
ISR(USART_UDRE_vect) {
    if (tx_head != tx_tail) {
        // There is data in the buffer
        UDR0 = tx_buffer[tx_tail];
        tx_tail = (tx_tail + 1) & TX_BUFFER_MASK;
    } else {
        // Buffer is empty, disable interrupt
        UCSR0B &= ~(1 << UDRIE0);
    }
}
