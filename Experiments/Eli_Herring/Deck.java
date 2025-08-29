package Experiments.Eli_Herring;

public class Deck {
    
    
    Card[] deck;


    //Creates a deck for a specific game
    //"52" is a standard deck of cards
    //"Euchre" is a deck of cards with fewer cards
    //Maybe change to a switch statement 
    public Deck(String type){
        if (type == "Standard"){

            deck = new Card[52];

            for(int i = 1; i < 14; i++){
                Card temp = new Card(i,"Spades");
                deck[i - 1] = temp;
            }

            for(int i = 1; i < 14; i++){
                Card temp = new Card(i,"Hearts");
                deck[i - 1 + 13] = temp;
            }

            for(int i = 1; i < 14; i++){
                Card temp = new Card(i,"Clubs");
                deck[i - 1 + 26] = temp;
            }

            for(int i = 1; i < 14; i++){
                Card temp = new Card(i,"Diamonds");
                deck[i - 1 + 39] = temp;
            }




        } 
        
    }
    
}
