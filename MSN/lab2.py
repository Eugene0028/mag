import json
import re
from dataclasses import dataclass, asdict
from typing import List, Optional

import requests
from lxml import html


@dataclass
class Vacancy:
    title: str
    salary_min: Optional[int]
    salary_max: Optional[int]
    currency: Optional[str]
    url: str
    source: str  # hh.ru или superjob.ru и т.п.

api_url = "https://api.hh.ru/vacancies"


def parse_salary_hh(s: Optional[str]) -> tuple[Optional[int], Optional[int], Optional[str]]:
    if not s:
        return None, None, None

    s = s.replace("\u202f", " ").replace("\xa0", " ").strip()

    # Выделяем валюту — обычно последнее слово
    parts = s.split()
    currency = parts[-1] if parts else None

    # Выделяем все числа в строке
    numbers = [int("".join(re.findall(r"\d+", part))) for part in parts if re.search(r"\d", part)]

    if "от" in s and numbers:
        return numbers[0], None, currency
    if "до" in s and numbers:
        return None, numbers[0], currency
    if "–" in s or "-" in s:
        # Диапазон
        if len(numbers) >= 2:
            return numbers[0], numbers[1], currency
    if numbers:
        # Одна конкретная цифра
        return numbers[0], numbers[0], currency

    return None, None, currency


def fetch_hh_vacancies_html(query: str, pages: int) -> List[Vacancy]:
    """
    Получение вакансий с hh.ru парсингом HTML (XPath + lxml).
    Используется в 2‑й лабораторной для демонстрации работы с DOM/XPath.
    """
    vacancies: List[Vacancy] = []

    base_url = "https://hh.ru/search/vacancy"
    headers = {
        "User-Agent": "Mozilla/5.0 (compatible; MSN-lab2-bot/1.0)",
    }

    for page in range(pages):
        params = {
            "text": query,
            "page": page,
        }
        print(f"Загружаю страницу {page + 1} hh.ru (HTML)...")
        resp = requests.get(base_url, params=params, headers=headers)
        if resp.status_code != 200:
            print(f"Не удалось загрузить HTML страницу {page + 1}: HTTP {resp.status_code}")
            break

        tree = html.fromstring(resp.text)

        # Каждый блок вакансии
        vacancy_nodes = tree.xpath('//div[@data-qa="vacancy-serp__vacancy"]')
        if not vacancy_nodes:
            # На случай, если структура изменилась
            vacancy_nodes = tree.xpath('//div[contains(@class, \"serp-item\")]')

        for node in vacancy_nodes:
            # Заголовок и ссылка
            title_nodes = node.xpath('.//a[@data-qa=\"vacancy-serp__vacancy-title\"]')
            if not title_nodes:
                # fallback
                title_nodes = node.xpath('.//a[contains(@class, \"serp-item__title\")]')
            if not title_nodes:
                continue

            title_el = title_nodes[0]
            title = (title_el.text_content() or "").strip()
            url = title_el.get("href") or ""

            # Зарплата
            salary_texts = node.xpath('.//span[@data-qa=\"vacancy-serp__vacancy-compensation\"]/text()')
            salary_str = salary_texts[0].strip() if salary_texts else None
            salary_min, salary_max, currency = parse_salary_hh(salary_str)

            vacancies.append(
                Vacancy(
                    title=title,
                    salary_min=salary_min,
                    salary_max=salary_max,
                    currency=currency,
                    url=url,
                    source="hh.ru",
                )
            )

    return vacancies


def fetch_vacancies(query: str, pages: int, per_page: int = 20) -> List[Vacancy]:
    vacancies: List[Vacancy] = []


    headers = {
        "User-Agent": "Mozilla/5.0 (compatible; MSN-lab2-bot/1.0)",
    }

    for page in range(pages):
        params = {
            "text": query,
            "page": page,
            "per_page": per_page,
        }
        print(f"Загружаю страницу {page + 1} hh.ru ...")
        resp = requests.get(api_url, params=params, headers=headers)
        if resp.status_code != 200:
            print(f"Не удалось загрузить данные API для страницы {page + 1}: HTTP {resp.status_code}")
            break

        data = resp.json()
        for item in data.get("items", []):
            title = (item.get("name") or "").strip()
            url = item.get("alternate_url") or ""

            salary_obj = item.get("salary") or {}
            salary_min = salary_obj.get("from")
            salary_max = salary_obj.get("to")
            currency = salary_obj.get("currency")

            vacancies.append(
                Vacancy(
                    title=title,
                    salary_min=salary_min,
                    salary_max=salary_max,
                    currency=currency,
                    url=url,
                    source="hh.ru",
                )
            )

    return vacancies



def main():
    query = input("Введите должность для поиска (например, 'Python разработчик'): ").strip()
    if not query:
        print("Должность не указана — выхожу.")
        return

    try:
        pages = int(input("Сколько страниц hh.ru разобрать? (например, 2): ").strip())
    except ValueError:
        print("Некорректное число страниц — выхожу.")
        return

    if pages <= 0:
        print("Число страниц должно быть положительным.")
        return

    vacancies = fetch_hh_vacancies_html(query, pages)
    print(f"Найдено вакансий: {len(vacancies)}")

    data = [asdict(v) for v in vacancies]
    filename = "vacancies_hh.json"
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"Результат сохранён в файл {filename}")


if __name__ == "__main__":
    main()


