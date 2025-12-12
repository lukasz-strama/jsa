# Workflow

Nie pracujemy bezpośrednio na gałęzi `main`\! Każda zmiana to nowa gałąź (branch).

### 1\. Start

```bash
# Przełącz się na główną gałąź
git checkout main

# Pobierz zmiany
git pull

# Stwórz nową gałąź dla swojego zadania i przełącz się na nią
git checkout -b kategoria/nazwa-zadania
```

### 2\. Commit

Koniec logicznego fragmentu:

```bash
git status

# Dodaj pliki do zatwierdzenia, kropka dodaje wszystko
git add . 

git commit -m "Add SerialPortService class to list available COM ports"
```

W branchu może być wiele commitów!!

### 3\. Push

Wysyłasz swoją gałąź na serwer.

```bash
git push -u origin kategoria/nazwa-zadania
```

### 4\. Pull Request (PR)

Na GitHubie:

1.  Wejdź w zakładkę **Pull Requests**.
2.  Kliknij **New Pull Request**.
3.  Wybierz swoją gałąź -\> `main`.
4.  Nadaj tytuł, przypisz **Label** (np. `java / gui`) i **Assignee** (Siebie).
5.  Kliknij **Create Pull Request**.

### 5\. Code Review i Merge

1.  Poczekaj, aż **GitHub Actions** zaświeci się na zielono ✅ (znaczy, że kod się buduje).
2.  Ktoś wchodzi, sprawdza zmiany i daje **Approve**.
3.  Klikasz **Squash and merge** (lub Merge pull request).
4.  Usuwasz starą gałąź **Delete branch**.

-----

### Konwencja nazewnictwa gałęzi

  * `feature/...` – nowa funkcjonalność (np. nowy wykres).
  * `fix/...` – naprawa błędu (np. poprawa obliczeń RMS).
  * `refactor/...` – czyszczenie kodu bez zmiany działania.
  * `test/...` – dodanie lub poprawa testów.
  * `chore/...` – zmiany w konfiguracji, skryptach budujących itp.
  * `docs/...` – zmiany tylko w dokumentacji.