/**
 * @file main.c
 * @brief Firmware entry point for the JSignalAnalysis oscilloscope.
 *
 * Initialises all peripherals (GPIO, UART, ADC, Timer1), enables the
 * watchdog timer (2 s timeout), and enters the main command-processing
 * loop. Supported single-byte commands:
 *
 * | Byte | Meaning                    |
 * |------|----------------------------|
 * | 0x01 | Start data acquisition     |
 * | 0x02 | Stop data acquisition      |
 * | 0x10 | Set sample rate to 1 kHz   |
 * | 0x11 | Set sample rate to 10 kHz  |
 * | 0x12 | Set sample rate to 20 kHz  |
 * | 0x3F | Handshake / identify ('?') |
 */

#include <avr/io.h>
#include <avr/interrupt.h>
#include <avr/wdt.h>
#include <util/delay.h>
#include "uart.h"
#include "adc.h"
#include "timer.h"
#include "gpio.h"

// Command Definitions
#define CMD_START 0x01
#define CMD_STOP 0x02
#define CMD_RATE_1KHZ 0x10
#define CMD_RATE_10KHZ 0x11
#define CMD_RATE_20KHZ 0x12
#define CMD_ID 0x3F // '?'

int main(void)
{
    // Startup Delay to prevent WDT reset loops if power is unstable
    _delay_ms(50);

    GPIO_InitSafe();
    UART_Init();
    ADC_Init();
    Timer1_Init();

    // Enable Watchdog Timer (2 Seconds) to protect against hangs
    wdt_enable(WDTO_2S);

    sei();

    while (1)
    {
        wdt_reset();

        if (UART_IsDataAvailable())
        {
            uint8_t cmd = UART_ReceiveByte();

            switch (cmd)
            {
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

            case CMD_RATE_20KHZ:
                Timer1_SetFrequency(SAMPLE_RATE_20KHZ);
                break;

            case CMD_ID:
            {
                // Send ID string and calculate XOR checksum for integrity verification
                const char *id_str = "OSC_V1\n";
                uint8_t checksum = 0;
                const char *p = id_str;
                while (*p)
                {
                    UART_SendByte(*p);
                    checksum ^= *p;
                    p++;
                }
                UART_SendByte(checksum);
            }
            break;

            default:
                // Ignore unknown commands to prevent undefined behavior
                break;
            }
        }
    }

    return 0;
}
