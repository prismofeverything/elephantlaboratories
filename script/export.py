import math
import pandas as pd

from pathlib import Path


backer_columns = [
    'Backer Number',
    'Backer UID',
    'Backer Name',
    'Email',
    'Shipping Country',
    'Shipping Amount',
    'Reward Title',
    'Backing Minimum',
    'Reward ID',
    'Bonus Support',
    'Pledge Amount',
    'Pledged At',
    'Fulfillment Status',
    'Pledged Status',
    'Notes',
    'Wooden Insert',
    'Sol: Last Days of a Star',
    'Survey Response',
    'Shipping Name',
    'Shipping Address 1',
    'Shipping Address 2',
    'Shipping City',
    'Shipping State',
    'Shipping Postal Code',
    'Shipping Country Name',
    'Shipping Country Code',
    'Shipping Phone Number',
    'Shipping Delivery Notes']


export_columns = [
    'Order #',
    'Recipient Full Name',
    'Recipient Email',
    'Recipient Phone',
    'Recipient Company',
    'Address Line 1',
    'Address Line 2',
    'Address Line 3',
    'City',
    'State',
    'Postal Code',
    'Country Code',
    'Item SKU',
    'Item Name / Title',
    'Item Quantity',
    'Item Weight (oz)']


empty_columns = [
    'Address Line 3',
    'Recipient Company']


direct_translations = {
    'Order #': 'Backer Number',
    'Recipient Full Name': 'Shipping Name',
    'Recipient Email': 'Email',
    'Recipient Phone': 'Shipping Phone Number',
    'Address Line 1': 'Shipping Address 1',
    'Address Line 2': 'Shipping Address 2',
    'City': 'Shipping City',
    'State': 'Shipping State',
    'Postal Code': 'Shipping Postal Code',
    'Country Code': 'Shipping Country Code'}


spiral_translations = {
    'Client Order No.': 'Backer Number',
    'NAME': 'Shipping Name',
    'EMAIL': 'Email',
    'TELEPHONE': 'Shipping Phone Number',
    'ADDRESS1': 'Shipping Address 1',
    'ADDRESS2': 'Shipping Address 2',
    'CITY': 'Shipping City',
    'STATE/COUNTY': 'Shipping State',
    'POSTAL_CODE': 'Shipping Postal Code',
    'COUNTRY': 'Shipping Country Code',
    'Sol Base game': 'Sol: Last Days of a Star',
    'Sol Insert': 'Wooden Insert',
}


products = {
    'EPH112': {
        'sku': 'EPH112',
        'name': 'Sol - Base Game',
        'weight': 70.55},
    'EPH121': {
        'sku': 'EPH121',
        'name': 'Sol - Wooden Insert',
        'weight': 1.76},
    'EPH131': {
        'sku': 'EPH131',
        'name': 'Sol - Upgrade Pack',
        'weight': 3.53}}


def read_backer_report(path):
    report = pd.read_csv(path)
    return report


def read_excel_file(path):
    excel = pd.read_excel(path, sheet_name=0)
    return excel


def item_fields(sku, quantity):
    product = products[sku]
    result = {
        'Item SKU': sku,
        'Item Name / Title': product['name'],
        'Item Quantity': quantity,
        'Item Weight (oz)': product['weight']}

    return result


def isnan(value):
    return isinstance(value, float) and math.isnan(value)


def translate_spiral(row):
    result = {}

    if (row['Sol: Last Days of a Star'] == 0 and row['Wooden Insert'] == 0) or empty_value(row['Shipping Country Code']):
        return result

    for export_key, backer_key in spiral_translations.items():
        update = row[backer_key]
        if empty_value(update):
            update = ''

        result[export_key] = update

    result['Order Value Currency'] = 'USD'
    result['Tracking'] = 'Y'

    if row['Sol: Last Days of a Star'] == 0 and row['Wooden Insert'] == 1:
        result['Sol 2nd edition upgrade pack'] = 1
    else:
        result['Sol 2nd edition upgrade pack'] = 0

    if isinstance(row['Pledge Amount'], float):
        import ipdb; ipdb.set_trace()

    pledge_amount = float(row['Pledge Amount'][1:])
    shipping_amount = float(row['Shipping Amount'][1:])

    result['Order Value'] = pledge_amount - shipping_amount
    result['Carriage'] = shipping_amount
    result['POSTAL_CODE'] = str(result['POSTAL_CODE'])

    if empty_value(result['EMAIL']):
        result['EMAIL'] = 'mothership@elephantlaboratories.com'

    return result


def translate_row(row):
    result = {}

    for export_key, backer_key in direct_translations.items():
        value = row[backer_key]
        if backer_key == 'Shipping Address 2' and isnan(value):
            result[export_key] = ''
        else:
            result[export_key] = value

    for empty in empty_columns:
        result[empty] = ''

    insert_count = row['Wooden Insert']
    game_count = row['Sol: Last Days of a Star']

    if insert_count == 0:
        if game_count == 0:
            return []

        game_fields = item_fields(
            'EPH112',
            game_count)
        result.update(game_fields)
        return [result]

    elif game_count == 0:
        insert_fields = item_fields(
            'EPH121',
            insert_count)

        upgrade_fields = item_fields(
            'EPH131',
            1)

        result.update(insert_fields)
        upgrade_result = result.copy()
        upgrade_result.update(upgrade_fields)

        return [result, upgrade_result]

    else:
        game_fields = item_fields(
            'EPH112',
            game_count)
        
        insert_fields = item_fields(
            'EPH121',
            insert_count)

        result.update(game_fields)
        insert_result = result.copy()
        insert_result.update(insert_fields)

        return [result, insert_result]


def empty_value(value):
    return value is None or value == '' or isnan(value)


def merge_row(existing, row):
    merged = {}

    for key, value in existing.items():
        new_value = row[key]
        if key in row and empty_value(value) or not empty_value(new_value):
            merged[key] = new_value
        else:
            merged[key] = value

    return merged


def append_row(df, row):
    df.loc[len(df)] = row
    return df


def open_updated_report(input_path, replace_path):
    backers = read_backer_report(
        input_path)

    replace = read_backer_report(
        replace_path)

    report = pd.concat(
        [backers, replace],
        ignore_index=True)

    columns = {
        column_name: report[column_name]
        for column_name in backer_columns}

    return report, columns


def export_backer_report(countries=None):
    base_path = Path('~/elabs/sol-reprint/campaign/backers-export/')

    input_path = base_path / 'sol-reprint-6-1-2024.csv'
    replace_path = base_path / 'sol-problem-addresses.csv'
    output_path = base_path / 'sol-crwn-export-jan-6-2024.csv'

    report, columns = open_updated_report(
        input_path,
        replace_path)

    export = pd.DataFrame(
        columns=export_columns)

    report_entries = {}
    for index, row in enumerate(zip(*columns.values())):
        row_entries = dict(
            zip(backer_columns, row))
        report_entries[
            row_entries['Backer Number']] = row_entries

    for row in report_entries.values():
        country = row['Shipping Country Code']
        if countries is None or country in countries:
            translate = translate_row(row)
            for entry in translate:
                export = append_row(
                    export,
                    entry)

    export.to_csv(
        output_path,
        index=False)


def export_spiral_report(exclude=None):
    exclude = exclude or []

    base_path = Path('~/elabs/sol-reprint/campaign/backers-export/')

    template_path = base_path / 'sol-spiral-report.xlsx'
    input_path = base_path / 'sol-reprint-6-1-2024.csv'
    replace_path = base_path / 'sol-problem-addresses.csv'
    output_path = base_path / 'sol-spiral-export-6-jan-2023.xlsx'

    report, columns = open_updated_report(
        input_path,
        replace_path)

    excel = read_excel_file(template_path)

    report_entries = {}

    for index, row in enumerate(zip(*columns.values())):
        row_entries = dict(
            zip(backer_columns, row))
        backer_number = row_entries['Backer Number']
        if backer_number in report_entries:
            report_entries[backer_number] = merge_row(
                report_entries[backer_number],
                row_entries)
        else:
            report_entries[backer_number] = row_entries

    for row in report_entries.values():
        if row['Shipping Country Code'] not in exclude:
            translate = translate_spiral(row)
            if translate:
                excel = append_row(
                    excel,
                    translate)

    excel.to_excel(
        output_path,
        index=False)


if __name__ == '__main__':
    # export_type = 'CRWN'
    export_type = 'SG'

    if export_type == 'CRWN':
        export_backer_report(
            countries=[
                'US',
                'CA'])
    else:
        export_spiral_report(
            exclude=[
                'US',
                'CA'])
