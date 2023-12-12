import math
import pandas as pd


def read_backer_report(path):
    report = pd.read_csv(path)
    return report


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
    'Recipient Full Name': 'Backer Name',
    'Recipient Email': 'Email',
    'Recipient Phone': 'Shipping Phone Number',
    'Address Line 1': 'Shipping Address 1',
    'Address Line 2': 'Shipping Address 2',
    'City': 'Shipping City',
    'State': 'Shipping State',
    'Postal Code': 'Shipping Postal Code',
    'Country Code': 'Shipping Country Code'}


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


def item_fields(sku, quantity):
    product = products[sku]
    result = {
        'Item SKU': sku,
        'Item Name / Title': product['name'],
        'Item Quantity': quantity,
        'Item Weight (oz)': product['weight']}

    return result


def translate_row(row):
    result = {}

    for export_key, backer_key in direct_translations.items():
        value = row[backer_key]
        if backer_key == 'Shipping Address 2' and isinstance(value, float) and math.isnan(value):
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


def append_row(df, row):
    df.loc[len(df)] = row
    return df


def export_backer_report(countries=None):
    input_path = 'sol-reprint-12-10-23.csv'
    replace_path = 'sol-problem-addresses.csv'
    output_path = 'sol-crwn-export-12-10-2023.csv'

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


if __name__ == '__main__':
    export_backer_report(
        countries=[
            'US',
            'CA'])
