import pandas as pd


EXCHANGE_DECEMBER_2022 = 0.8276
EXCHANGE_MAY_2024 = 1.0 / 1.2711

def sum_column(path, column, filter, transform, exchange):
    frame = pd.read_csv(path)

    for key, match in filter.items():
        frame = frame[frame[key] == match]

    transformed = [
        transform(value) * exchange
        for value in frame[column]]

    return sum(transformed)


def usd_float(s):
    return float(s.split(' ')[0])


def print_vat(total):
    vat = total / 6.0
    base = total - vat

    print(f'total: {total}\nbase: {base}\nvat: {vat}')


def test_sum_column():
    path = '/home/pattern/Downloads/Estimate-Sol- Last Days of a Star 2nd Edition.csv'
    total = sum_column(
        path,
        'Customs Value',
        {'Country': 'UNITED KINGDOM'},
        usd_float,
        EXCHANGE_DECEMBER_2022)

    print_vat(total)


def test_backer_report():
    path = '/home/pattern/code/elephantlaboratories/dump/uk-backers.csv'
    total = sum_column(
        path,
        'Pledge Amount',
        {'Country': 'UNITED KINGDOM'},
        usd_float,
        EXCHANGE_DECEMBER_2022)
    
    print_vat(total)


def test_vat():
    may_jul = 1420.34 * EXCHANGE_MAY_2024

    print_vat(may_jul)


if __name__ == '__main__':
    test_sum_column()
    test_vat()
