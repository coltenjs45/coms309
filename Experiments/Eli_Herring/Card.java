package Experiments.Eli_Herring;


public class Card {
    
    //Value of the card,  ie ace, king, 4, 5 ...
    //King = 13, Queen = 12, Jack = 11 // can change to use V or T or whatever 
    //Should be bounded 0 < x < 14 for ints
    public int value;
    
    //Priority of the card, for games with trump suits this can denote the priority 
    //for instance, if spades are trump the value will be true
    public boolean trump;

    //Suit of the card, hearts, spades, diamonds, clubs
    public String suit;


    //default constructor, basically a null card
    public Card(){
        
    }


    //Card constructor with trump
    public Card(int value, String suit, boolean trump){
        this.value = value;
        this.suit = suit;
        this.trump = trump;
    }

    //Card constructor without trump
    public Card(int value, String suit){
        this.value = value;
        this.suit = suit; 
        trump = false;
    }


    public int getValue(){
        return value;
    }

    public void setValue(int value){
        this.value = value;
    }

    public boolean getTrump(){
        return trump;
    }

    public void setTrump(boolean trump){
        this.trump = trump;
    }

    public String getSuit(){
        return suit;
    }

    public void setSuit(String suit){
        this.suit = suit;
    }

    @Override
    public String toString(){
        
        return "Value: " + value + "\nSuit: " + suit + "\nTrump: " + trump + "\n";
    }

}
