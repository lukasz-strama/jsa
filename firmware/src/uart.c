#include "uart.h"
#include "gpio.h"
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
    UBRR0H = (uint8_t)(UBRR_VALUE >> 8);
    UBRR0L = (uint8_t)UBRR_VALUE;

    // Enable Double Speed Mode (U2X0) for better baud rate accuracy at 115200
    UCSR0A |= (1 << U2X0);

    // Enable Receiver and Transmitter
    UCSR0B = (1 << RXEN0) | (1 << TXEN0);

    // Frame format: 8 data bits, 1 stop bit, No parity
    UCSR0C = (1 << UCSZ01) | (1 << UCSZ00);
}

void UART_SendByte(uint8_t data) {
    uint8_t next_head = (tx_head + 1) & TX_BUFFER_MASK;

    if (next_head != tx_tail) {
        tx_buffer[tx_head] = data;
        tx_head = next_head;
        
        // Enable Data Register Empty Interrupt to start transmission
        UCSR0B |= (1 << UDRIE0);
    } else {
        // Buffer Overflow: Drop data and indicate error via LED
        GPIO_ToggleLED();
    }
}

uint8_t UART_ReceiveByte(void) {
    // Blocking wait for data
    while (!(UCSR0A & (1 << RXC0)));
    return UDR0;
}

uint8_t UART_IsDataAvailable(void) {
    return (UCSR0A & (1 << RXC0));
}

ISR(USART_UDRE_vect) {
    if (tx_head != tx_tail) {
        UDR0 = tx_buffer[tx_tail];
        tx_tail = (tx_tail + 1) & TX_BUFFER_MASK;
    } else {
        // Buffer empty, disable interrupt to prevent infinite ISR calls
        UCSR0B &= ~(1 << UDRIE0);
    }
}
