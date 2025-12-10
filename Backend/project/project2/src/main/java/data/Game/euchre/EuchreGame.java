package data.Game.euchre;

import data.Game.euchre.history.EuchreMatchEventEntity;
import data.Game.euchre.history.EuchreMatchHistoryEntity;
import data.Game.goFish.history.GoFishMatchHistoryEntity;
import data.User.Stats.EuchreStats;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an entire Euchre game session, including bidding,
 * dealing, trick-taking, scoring, and round progression.
 * Stores player order, teams, the deck, current trick state,
 * and all gameplay-related rules.
 */
public class EuchreGame {
    private List<EuchrePlayer> players;
    private EuchreDeck deck;
    private List<EuchreCard> currentTrick;
    private int currentPlayerIndex;
    private int currentDealerIndex;
    private char trumpSuit;
    private char leadSuit;
    private EuchreCard optionCard;
    private boolean isBidding;
    private boolean isChoice;
    private EuchreTeam teamOne;
    private EuchreTeam teamTwo;
    private EuchreMatchHistoryEntity matchHistory;

    /**
     * Basic constructor that takes a list of players in the game
     * @param players (List of players)
     */
    public EuchreGame(List<EuchrePlayer> players) {
        this.players = players;
        this.deck = new EuchreDeck();
        this.currentDealerIndex = 0;
        this.currentPlayerIndex = 1;
        teamOne = new EuchreTeam(players.get(0), players.get(2));
        teamTwo = new EuchreTeam(players.get(1), players.get(3));
    }

    /**
     * Starts the round by dealing everyone one their hand and revealing the option card
     * @return optionCard
     */
    public void startGame() {
        this.currentTrick = new ArrayList<>();
        deck.shuffle();
        this.dealCards();
        this.isBidding = true;
        this.isChoice = false;
        optionCard = deck.dealCard();

        // Log game start
        logEvent(
                "ROUND_START",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                teamOne.getScore(),
                teamTwo.getScore(),
                getCurrentDealerUsername(),
                optionCard.toString()
        );

        System.out.println("New round started Dealer: " + getCurrentDealerUsername() + " || " + getCurrentPlayerUsername() + "'s turn!");
        System.out.println("Option Card: " + optionCard);
    }

    /**
     * Gets the option card
     * @return option card
     */
    public EuchreCard getOptionCard() {
        return optionCard;
    }

    /**
     * Gets the current trick
     * @return current trick
     */
    public List<EuchreCard> getCurrentTrick() {
        return currentTrick;
    }

    /**
     * Starts a new round by dealing everyone one their hand and revealing the option card
     * @return optionCard
     */
    public EuchreCard startNewRound() {
        deck = new EuchreDeck();
        this.currentTrick = new ArrayList<>();
        currentDealerIndex = nextPlayerIndex(currentDealerIndex);
        currentPlayerIndex = nextPlayerIndex(currentDealerIndex);
        teamOne.clearTricksTaken();
        teamTwo.clearTricksTaken();
        teamOne.setTeamMemberWhenAlone(false);
        teamOne.setTeamPickedUpCard(false);
        teamTwo.setTeamMemberWhenAlone(false);
        teamTwo.setTeamPickedUpCard(false);
        this.dealCards();
        this.isChoice = false;
        this.isBidding = true;
        optionCard = deck.dealCard();

        // Log game start
        logEvent(
                "ROUND_START",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                teamOne.getScore(),
                teamTwo.getScore(),
                getCurrentDealerUsername(),
                optionCard.toString()
        );

        System.out.println("New round started Dealer: " + getCurrentDealerUsername() + " || " + getCurrentPlayerUsername() + "'s turn!");
        System.out.println("Option Card: " + optionCard);
        return optionCard;
    }

    /**
     * Handles passes that occur during the bidding phase
     */
    public void playerPasses() {
        // Increment current player
        currentPlayerIndex = nextPlayerIndex(currentPlayerIndex);

        // If it is the second round and everyone has passed, dealer must choose suit
        if (isChoice && (currentPlayerIndex == currentDealerIndex)) {
            System.out.println("Dealer must choose a suit!");
        }

        // If we completed the first round of bidding, players now select suit
        if (!isChoice && currentPlayerIndex == currentDealerIndex) {
            isChoice = true;
        }

        // Log Pass
        logEvent(
                "PASS",
                getCurrentPlayerUsername(),
                null,
                null,
                false,
                null,
                null,
                null,
                teamOne.getScore(),
                teamTwo.getScore(),
                getCurrentDealerUsername(),
                optionCard.toString()
        );
    }

    /**
     * Handles the event where the suit has been chosen
     * @param suit (suit chosen)
     */
    public void playerChoosesSuit(char suit) {
        trumpSuit = suit;
        isBidding = false;
        isChoice = false;

        // See what team choose to pick up the card and is the aggressor
        if (teamOne.getTeamMembers().contains(players.get(currentPlayerIndex))) {
            teamOne.setTeamPickedUpCard(true);
        } else {
            teamTwo.setTeamPickedUpCard(true);
        }

        // Log Choose Suit
        logEvent(
                "TRUMP_CHOSEN",
                players.get(currentPlayerIndex).getUsername(),
                (teamOne.getTeamMembers().contains(players.get(currentPlayerIndex))) ? "TEAM_1" : "TEAM_2",
                charSuitToString(suit),
                false,
                null,
                null,
                null,
                teamOne.getScore(),
                teamTwo.getScore(),
                getCurrentDealerUsername(),
                null
        );


        // Set current player to player next to dealer
        System.out.println(players.get(currentPlayerIndex).getUsername() + " has chosen " + charSuitToString(suit) + " as trump!");
        currentPlayerIndex = nextPlayerIndex(currentDealerIndex);
        System.out.println("It is now " + players.get(currentPlayerIndex).getUsername() + "'s turn!");
    }

    /**
     * Handles the event where the current player decides to pick up the card
     * @param droppedCard (card that player is removing)
     * @return true if function worked, false otherwise
     */
    public Boolean playerPicksUp(EuchreCard droppedCard) {
        EuchrePlayer dealer = players.get(currentDealerIndex);

        // Get Dealer Stats
        EuchreStats dealerStats = dealer.getStats();

        if (isBidding && dealer.getHand().contains(droppedCard)) {
            dealer.addCard(optionCard);
            dealer.removeCard(droppedCard);
            trumpSuit = optionCard.suit;
            isBidding = false;

            // See what team chose to pick up the card and is the aggressor
            if (teamOne.getTeamMembers().contains(players.get(currentPlayerIndex))) {
                teamOne.setTeamPickedUpCard(true);
            } else {
                teamTwo.setTeamPickedUpCard(true);
            }

            // Log Pickup
            logEvent(
                    "PICKED_UP",
                    dealer.getUsername(),
                    (teamOne.getTeamMembers().contains(dealer)) ? "TEAM_1" : "TEAM_2",
                    charSuitToString(trumpSuit),
                    false,
                    optionCard.toString(),
                    null,
                    null,
                    teamOne.getScore(),
                    teamTwo.getScore(),
                    getCurrentDealerUsername(),
                    null
            );

            System.out.println(dealer.getUsername() + " picked up the " + optionCard.toString());
            currentPlayerIndex = nextPlayerIndex(currentDealerIndex);
            System.out.println("It is now " + players.get(currentPlayerIndex).getUsername() + "'s turn!");

            // Increment time's picked up stat
            if (dealerStats != null) { dealerStats.addTimePickedUp(); }

            return true;
        } else {
            System.out.println("Not currently in bidding round or dropped card invalid!");
            return false;
        }
    }

    /**
     * Handles laying down a players card will be run four times for a loop
     * @param currentPlayer (player who lays the card
     * @param card (card that is being laid)
     * @return (Message describing what happened
     */
    public String takeTurn(EuchrePlayer currentPlayer, EuchreCard card) {
        // Check that player has requested card
        if (!currentPlayer.getHand().contains(card)) {
            return currentPlayer.getUsername() + " does not have the " + card.toString();
        }

        // Set lead suit if trick is empty
        if (currentTrick.isEmpty()) {
            leadSuit = card.getEffectiveSuit(trumpSuit);
        }

        // Gets the list of cards that this player can play
        List<EuchreCard> playableCards = currentPlayer.checkPlayableCards(leadSuit, trumpSuit);

        // Check if player is allowed to play card
        if (!playableCards.contains(card)) {
            return currentPlayer.getUsername() + " can not play the " + card.toString();
        }

        // Add card to current trick and remove it from their hand
        currentTrick.add(card);

        // Log Turn
        logEvent(
                "CARD_PLAYED",
                currentPlayer.getUsername(),
                (teamOne.getTeamMembers().contains(currentPlayer)) ? "TEAM_1" : "TEAM_2",
                charSuitToString(trumpSuit),
                false,
                card.toString(),
                currentTrick.size(),   // trick position
                null,
                teamOne.getScore(),
                teamTwo.getScore(),
                getCurrentDealerUsername(),
                null
        );

        currentPlayer.removeCard(card);
        currentPlayerIndex = nextPlayerIndex(currentPlayerIndex);


        return currentPlayer.getUsername() + " played the " + card.toString();
    }

    /**
     * Determines the winner of the current trick, awards the trick,
     * updates the next starting player, and clears the trick.
     *
     * @return the player who won the trick
     */
    public EuchrePlayer giveTrick() {

        // === 1. Determine winning card ===
        EuchreCard winningCard = determineWinningCard(currentTrick, trumpSuit, leadSuit);

        // === 2. Award trick to the correct team ===
        EuchrePlayer winner = winningCard.getOwner();

        // Get winner's stats
        EuchreStats winnerStats = winner.getStats();

        // Increment winner's tricks taken
        if (winnerStats != null) { winnerStats.addTrickTaken(); }

        if (teamOne.getTeamMembers().contains(winner)) {
            teamOne.incrementTricksTaken();
        } else {
            teamTwo.incrementTricksTaken();
        }

        // Log trick given
        logEvent(
                "TRICK_WON",
                winner.getUsername(),
                (teamOne.getTeamMembers().contains(winner)) ? "TEAM_1" : "TEAM_2",
                charSuitToString(trumpSuit),
                false,
                null,
                null,
                winner.getUsername(),
                teamOne.getScore(),
                teamTwo.getScore(),
                getCurrentDealerUsername(),
                null
        );

        System.out.println(winner.getUsername() + "'s " + winningCard + " wins the trick!");

        // === 3. Save the trick to the player's history ===
        winner.getTricks().add(new ArrayList<>(currentTrick));

        // === 4. The winner leads next ===
        currentPlayerIndex = players.indexOf(winner);

        // === 5. Clear trick owners and reset ===
        for (EuchreCard card : currentTrick) {
            card.setOwner(null);
        }
        currentTrick.clear();

        return winner;
    }


    /**
     * Called at the end of the round and gives points to winning team
     */
    public void givePoints() {
        // Give points based on score of each team and who is the aggressor

        // Team One picked up the card and is the aggressor
        if (teamOne.getTeamPickedUpCard()) {

            if (teamOne.getTricksTaken() == 5) {
                teamOne.incrementScore(2);
                System.out.println("Team One marched Team Two and gets two points!");

                // Keep track of stat for sweeps
                // Get both player's stats
                List<EuchrePlayer> winningTeam = teamOne.getTeamMembers();
                EuchreStats winnerStatsOne = winningTeam.get(0).getStats();
                EuchreStats winnerStatsTwo = winningTeam.get(1).getStats();

                // Increment Sweep for both players
                winnerStatsOne.addSweepWon();
                winnerStatsTwo.addSweepWon();

            } else if (teamOne.getTricksTaken() >= 3) {
                // Team One got the majority, they get one point
                teamOne.incrementScore(1);
                System.out.println("Team One got the majority and gets one point!");
            } else {
                // Team One did not get the majority, Team Two gets two points
                teamTwo.incrementScore(2);
                System.out.println("Team One got euchred and Team Two gets two points!");
            }
        } else {
            // Team Two picked up the card and is the aggressor

            if (teamTwo.getTricksTaken() == 5) {
                teamTwo.incrementScore(2);
                System.out.println("Team Two marched Team One and gets two points!");

                // Keep track of stat for sweeps
                // Get both player's stats
                List<EuchrePlayer> winningTeam = teamTwo.getTeamMembers();
                EuchreStats winnerStatsOne = winningTeam.get(0).getStats();
                EuchreStats winnerStatsTwo = winningTeam.get(1).getStats();

                // Increment Sweep for both players
                winnerStatsOne.addSweepWon();
                winnerStatsTwo.addSweepWon();

            } else if (teamTwo.getTricksTaken() >= 3) {
                // Team Two got the majority, they get one point
                teamTwo.incrementScore(1);
                System.out.println("Team Two got the majority and gets one point!");
            } else {
                // Team Two did not get the majority, Team One gets two points
                teamOne.incrementScore(2);
                System.out.println("Team Two got euchred and Team One gets two points!");
            }
        }
        // Log points given
        logEvent(
                "POINTS_AWARDED",
                null,
                (teamOne.getTricksTaken() > teamTwo.getTricksTaken()) ? "TEAM_1" : "TEAM_2",
                charSuitToString(trumpSuit),
                false,
                null,
                null,
                null,
                teamOne.getScore(),
                teamTwo.getScore(),
                getCurrentDealerUsername(),
                null
        );
    }

    /**
     * Checks if a team has met the scoring threshold
     * @return winning team
     */
    public EuchreTeam getWinner() {
        if (teamOne.getScore() >= 2) {
            System.out.println("Team One has won!");

            EuchreStats winnerOneStats = teamOne.getTeamMembers().get(0).getStats();
            EuchreStats winnerTwoStats = teamOne.getTeamMembers().get(1).getStats();
            EuchreStats loserOneStats = teamTwo.getTeamMembers().get(0).getStats();
            EuchreStats loserTwoStats = teamTwo.getTeamMembers().get(1).getStats();

            winnerTwoStats.addGameWon();
            winnerTwoStats.addGamePlayed();

            winnerOneStats.addGameWon();
            winnerOneStats.addGamePlayed();

            loserOneStats.addGamePlayed();
            loserTwoStats.addGamePlayed();

            // Log Win
            logEvent(
                    "GAME_WON",
                    null,
                    "TEAM_1",   // or TEAM_2
                    charSuitToString(trumpSuit),
                    false,
                    null,
                    null,
                    null,
                    teamOne.getScore(),
                    teamTwo.getScore(),
                    getCurrentDealerUsername(),
                    null
            );

            return teamOne;
        }

        if (teamTwo.getScore() >= 2) {
            System.out.println("Team Two has won!");

            EuchreStats winnerOneStats = teamTwo.getTeamMembers().get(0).getStats();
            EuchreStats winnerTwoStats = teamTwo.getTeamMembers().get(1).getStats();
            EuchreStats loserOneStats = teamOne.getTeamMembers().get(0).getStats();
            EuchreStats loserTwoStats = teamOne.getTeamMembers().get(1).getStats();

            winnerTwoStats.addGameWon();
            winnerTwoStats.addGamePlayed();

            winnerOneStats.addGameWon();
            winnerOneStats.addGamePlayed();

            loserOneStats.addGamePlayed();
            loserTwoStats.addGamePlayed();

            // Log Win
            logEvent(
                    "GAME_WON",
                    null,
                    "TEAM_1",   // or TEAM_2
                    charSuitToString(trumpSuit),
                    false,
                    null,
                    null,
                    null,
                    teamOne.getScore(),
                    teamTwo.getScore(),
                    getCurrentDealerUsername(),
                    null
            );

            return teamTwo;
        }

        return null;
    }

    /**
     * Gets the current player by the currentPlayerIndex
     * @return username
     */
    public String getCurrentPlayerUsername() {
        return players.get(currentPlayerIndex).getUsername();
    }

    /**
     * Gets the current dealer by the currentDealerIndex
     * @return username
     */
    public String getCurrentDealerUsername() {
        return players.get(currentDealerIndex).getUsername();
    }

    /**
     * Deals cards to all players in game
     */
    public void dealCards() {
        for (int i = 0; i < 5; i++) {
            for (EuchrePlayer player : players) {
                player.addCard(deck.dealCard(player));
            }
        }
    }

    /**
     * Returns all players in current game
     * @return List of players
     */
    public List<EuchrePlayer> getPlayers() {
        return players;
    }

    /**
     * Updates turn by incrementing it
     */
    public void nextTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    /**
     * Gets the index of the player after the given player
     * @param previousPlayerIndex (previous player)
     * @return nextPlayerIndex
     */
    public int nextPlayerIndex(int previousPlayerIndex) {
        return (previousPlayerIndex + 1) % players.size();
    }

    /**
     * Helper method that converts char to string to help with logging
     * @param suit (suit of card)
     * @return String of suit
     */
    public String charSuitToString(char suit) {
        return switch (suit) {
            case 'h' -> "hearts";
            case 'd' -> "diamonds";
            case 's' -> "spades";
            case 'c' -> "clubs";
            default -> "Invalid char given!";
        };
    }

    /**
     * Helps to check if a card is considered a trump
     * @param card (given card
     * @return true/false
     */
    public boolean findIfTrumpOrBower (EuchreCard card) {
        return card.getEffectiveSuit(trumpSuit) == trumpSuit;
    }

    /**
     * Get team one score
     * @return score
     */
    public int getTeamOneScore() {
        return teamOne.getScore();
    }

    /**
     * Get team two score
     * @return score
     */
    public int getTeamTwoScore() {
        return teamTwo.getScore();
    }

    /**
     * Determines winning card
     * @param trick current trick
     * @param trumpSuit trump
     * @param leadSuit lead
     * @return winning card
     */
    private EuchreCard determineWinningCard(List<EuchreCard> trick, char trumpSuit, char leadSuit) {
        EuchreCard winner = trick.get(0);

        for (int i = 1; i < trick.size(); i++) {
            EuchreCard challenger = trick.get(i);

            int winnerRank = getEuchreRank(winner, trumpSuit, leadSuit);
            int challengerRank = getEuchreRank(challenger, trumpSuit, leadSuit);

            if (challengerRank > winnerRank) {
                winner = challenger;
            }
        }

        return winner;
    }

    /**
     * Gets the euchre rank of a card
     * @param card card
     * @param trump trump suit
     * @param lead lead suit
     * @return value of card
     */
    private int getEuchreRank(EuchreCard card, char trump, char lead) {
        char effSuit = card.getEffectiveSuit(trump);

        // Right bower
        if (card.value == 11 && card.suit == trump) return 100;

        // Left bower
        if (card.value == 11 && effSuit == trump) return 99;

        // Other trump
        if (effSuit == trump) return 50 + card.value;

        // Lead suit (only if actually following lead)
        if (effSuit == lead) return 10 + card.value;

        // Not trump, not lead → cannot win
        return 0;
    }


    /**
     * Initializes a new match history using the lobby id as match id
     * @param matchId lobby id
     */
    public void initMatchHistory(String matchId) {
        EuchreMatchHistoryEntity history = new EuchreMatchHistoryEntity();
        history.setMatchId(matchId);
        history.setStartTime(LocalDateTime.now());
        this.matchHistory = history;
    }

    /**
     * Get match history
     * @return matchHistory
     */
    public EuchreMatchHistoryEntity getMatchHistory() {
        return matchHistory;
    }

    public void logEvent(
            String eventType,
            String player,
            String team,
            String trumpSuit,
            Boolean alone,
            String cardPlayed,
            Integer trickNumber,
            String trickWinner,
            Integer teamAScore,
            Integer teamBScore,
            String dealer,
            String upCard
    ) {
        EuchreMatchEventEntity event = new EuchreMatchEventEntity();

        event.setMatchHistory(this.matchHistory);
        event.setTimestamp(LocalDateTime.now());

        event.setEventType(eventType);
        event.setPlayer(player);
        event.setTeam(team);

        event.setTrumpSuit(trumpSuit);
        event.setAlone(alone);

        event.setCardPlayed(cardPlayed);
        event.setTrickNumber(trickNumber);

        event.setTrickWinner(trickWinner);

        event.setTeamAScore(teamAScore);
        event.setTeamBScore(teamBScore);

        event.setDealer(dealer);
        event.setUpCard(upCard);

        this.matchHistory.getEvents().add(event);
    }

    public void setTeamOne(EuchreTeam teamOne) {
        this.teamOne = teamOne;
    }

    public void setTeamTwo(EuchreTeam teamTwo) {
        this.teamTwo = teamTwo;
    }

    public EuchreTeam getTeamOne() {
        return teamOne;
    }

    public EuchreTeam getTeamTwo() {
        return teamTwo;
    }
}
