## 1. Instalacja wymagań 

### Opcja A: Użycie Chocolatey
Poprzez menedżer pakietów [Chocolatey](https://chocolatey.org/):

```powershell
choco install make avr-gcc avrdude python
```

### Opcja B: Instalacja ręczna

1.  **AVR Toolchain:**
      * Pobierz i zainstaluj [AVR 8-bit Toolchain dla Windows](https://www.microchip.com/en-us/tools-resources/develop/microchip-studio/gcc-compilers).
      * Dodaj folder `bin` z zainstalowanego pakietu do zmiennej środowiskowej systemowej PATH.
2.  **Make:**
      * Zainstaluj [GnuWin32 Make](http://gnuwin32.sourceforge.net/packages/make.htm) lub skorzystaj z wersji dołączonej do Git Bash (jeśli używasz).
3.  **AVRDUDE:**
      * Pobierz [AVRDUDE](https://github.com/avrdudes/avrdude) i dodaj ścieżkę do pliku wykonywalnego do zmiennej PATH.
4.  **Python:**
      * Zainstaluj Python 3 ze strony [python.org](https://www.python.org/).

## 2\. Budowanie Firmware'u

```cmd
cd firmware
make all
```

Powstanie plik `firmware.hex`.

## 3\. Wgrywanie Firmware'u (Flashing)

Podłącz ATmega328P (Arduino Uno) przez USB.
Zidentyfikuj swój port COM (np. `COM3`).

Plik Makefile na Windowsie domyślnie próbuje użyć portu `COM3`, jeśli Twoje urządzenie znajduje się na innym porcie, musisz go nadpisać w poleceniu:

```cmd
make flash PORT=COM4
```

## 4\. Uruchomienie testów integracyjnych

Skrypt testowy w Pythonie obsługuje automatyczne wykrywanie portu COM:

1.  Zależności:

    ```cmd
    pip install pyserial
    ```

2.  Uruchom testy:

    ```cmd
    python ../tests/integration_test.py
    ```

Jeśli automatyczne wykrywanie nie zadziała, podaj port ręcznie:

```cmd
python ../tests/integration_test.py --port COM4
```