import random
import numpy as np


class Deck():
    def __init__(self, suits):
        self.suits = set(['flare'] + suits)
        self.cards = []
        for suit in list(self.suits):
            self.cards += [suit] * 13


    def shuffle(self):
        random.shuffle(self.cards)


    def last_flare(self):
        reverse = self.cards[::-1]
        return reverse.index('flare')


    def cycle(self):
        self.shuffle()
        return self.last_flare()


    def cycles(self, samples):
        cycles = [
            self.cycle()
            for _ in range(samples)]

        results = np.zeros((len(self.cards),))
        for cycle in cycles:
            results[cycle] += 1
        return results


    def games_ended_by(self, samples):
        cycles = self.cycles(samples)
        history = np.flip(cycles)
        # outcome = history * 100 / samples
        outcome = np.cumsum(history) * 100 / samples

        return {
            index: card
            for index, card in enumerate(
                np.flip(outcome)[:-12])}
        

def test_deck():
    deck = Deck([
        'light',
        'space',
        'time',
        # 'energy',
        'mass'])

    outcome = deck.games_ended_by(100000)

    for index, time in outcome.items():
        print(f'{index}:  {time}')

    import ipdb; ipdb.set_trace()


if __name__ == '__main__':
    test_deck()
