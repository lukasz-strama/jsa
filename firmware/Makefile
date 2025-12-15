MCU = atmega328p
F_CPU = 16000000UL
BAUD = 115200
PROGRAMMER = arduino

# OS Detection and Platform Specific Settings
ifeq ($(OS),Windows_NT)
    # Windows Settings
    RM = del /Q
    FixPath = $(subst /,\,$(1))
    PORT ?= COM3
    EXT = .exe
else
    # Linux/Unix Settings
    RM = rm -f
    FixPath = $(1)
    PORT ?= /dev/ttyACM0
    EXT = 
endif

CC = avr-gcc
OBJCOPY = avr-objcopy
AVRDUDE = avrdude
CFLAGS = -Os -DF_CPU=$(F_CPU) -mmcu=$(MCU) -Wall -Wextra -Iinclude

SRC = src/main.c src/adc.c src/uart.c src/timer.c src/gpio.c
OBJ = $(SRC:.c=.o)

TARGET = firmware

all: $(TARGET).hex

$(TARGET).hex: $(TARGET).elf
	$(OBJCOPY) -O ihex -R .eeprom $< $@

$(TARGET).elf: $(OBJ)
	$(CC) $(CFLAGS) -o $@ $^

%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

flash: $(TARGET).hex
	$(AVRDUDE) -F -V -c $(PROGRAMMER) -p $(MCU) -P $(PORT) -b 115200 -U flash:w:$(TARGET).hex

clean:
	$(RM) $(call FixPath,src/*.o) $(call FixPath,$(TARGET).elf) $(call FixPath,$(TARGET).hex)

.PHONY: all flash clean