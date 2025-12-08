import json
import string
from collections import Counter
from urllib.request import urlopen, Request


ALICE_URL = "https://www.gutenberg.org/cache/epub/11/pg11.txt"
GITHUB_USER_REPOS_URL = "https://api.github.com/users/{username}/repos"


def download_text(url: str) -> str:
    """
    Скачивание текста по URL и декодирование в строку.
    Используется стандартная библиотека, чтобы не требовались внешние зависимости.
    """
    with urlopen(url) as response:
        data = response.read()
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        return data.decode("latin-1")


def build_word_stats(text: str) -> dict:
    """
    Очистка текста от пунктуации, приведение к нижнему регистру
    и подсчёт числа вхождений каждого слова.
    Возвращает Питоновский словарь {слово: количество}, отсортированный по убыванию частоты.
    """
    # Дополнительно удаляем некоторые типографские символы
    extra_punct = "“”‘’—…"
    translator = str.maketrans("", "", string.punctuation + extra_punct)

    cleaned_text = text.translate(translator).lower()
    words = cleaned_text.split()

    counter = Counter(words)

    # Сортировка по убыванию количества вхождений
    sorted_items = sorted(counter.items(), key=lambda item: item[1], reverse=True)
    sorted_dict = dict(sorted_items)
    return sorted_dict


def show_top_words(word_stats: dict, top_n: int = 20) -> None:
    """Печать первых top_n самых частых слов из словаря статистики."""
    print(f"Top {top_n} words:")
    for i, (word, count) in enumerate(word_stats.items()):
        if i >= top_n:
            break
        print(f"{word!r}: {count}")


def fetch_github_user_repos(username: str, token=None) -> list:
    """
    Обращение к GitHub API для получения списка репозиториев пользователя.
    Возвращает JSON-данные (список репозиториев как Python-объект).
    """
    url = GITHUB_USER_REPOS_URL.format(username=username)
    # GitHub требует User-Agent в заголовках
    headers = {"User-Agent": "MSN-lab1-script"}
    # Авторизация через персональный токен (Personal Access Token)
    if token:
        headers["Authorization"] = f"token {token}"
    req = Request(url, headers=headers)
    with urlopen(req) as response:
        data = json.load(response)
    return data


def save_json(data, filename: str) -> None:
    """
    Сохранение произвольных данных в JSON-файл.
    """
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def main():
    # --- Задание 1: работа с текстом "Алиса в стране чудес" ---
    print("Скачиваю текст 'Alice's Adventures in Wonderland'...")
    alice_text = download_text(ALICE_URL)

    print("Строю статистику по словам...")
    word_stats = build_word_stats(alice_text)

    # word_stats — это питоновский словарь {слово: количество}, отсортированный по убыванию
    show_top_words(word_stats, top_n=20)

    # При желании можно сохранить статистику в JSON-файл
    save_json(word_stats, "alice_word_stats.json")
    print("Статистика слов сохранена в 'alice_word_stats.json'.")

    # --- Задание 2: работа с API GitHub ---
    username = input("\nВведите логин пользователя GitHub: ").strip()
    if not username:
        print("Логин не введён, пропускаю запрос к GitHub API.")
        return

    token = input("Введите GitHub Personal Access Token (можно оставить пустым): ").strip()

    print(f"Запрашиваю список репозиториев пользователя '{username}' через GitHub API...")
    repos_data = fetch_github_user_repos(username, token or None)

    output_filename = f"{username}_repos.json"
    save_json(repos_data, output_filename)
    print(f"JSON-вывод GitHub API сохранён в файле '{output_filename}'.")


if __name__ == "__main__":
    main()


