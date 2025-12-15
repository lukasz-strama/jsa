#include <avr/io.h>
#include <avr/interrupt.h>
#include "uart.h"
#include "adc.h"
#include "timer.h"

// Command Definitions
#define CMD_START       0x01
#define CMD_STOP        0x02
#define CMD_RATE_1KHZ   0x10
#define CMD_RATE_10KHZ  0x11

int main(void) {
    // Initialize all modules
    UART_Init();
    ADC_Init();
    Timer1_Init();

    // Enable Global Interrupts
    sei();

    // Main Loop
    while (1) {
        // Check for incoming commands from PC
        if (UART_IsDataAvailable()) {
            uint8_t cmd = UART_ReceiveByte();

            switch (cmd) {
                case CMD_START:
                    Timer1_Start();
                    break;
                
                case CMD_STOP:
                    Timer1_Stop();
                    break;
                
                case CMD_RATE_1KHZ:
                    Timer1_SetFrequency(SAMPLE_RATE_1KHZ);
                    break;
                
                case CMD_RATE_10KHZ:
                    Timer1_SetFrequency(SAMPLE_RATE_10KHZ);
                    break;
                
                default:
                    // Ignore unknown commands
                    break;
            }
        }
    }

    return 0;
}
