package Experiments.Eli_Herring;


public class Test {

    public static void main(String args[]){
        Deck cards = new Deck("Standard");

        for(int i = 0; i < 52; i++){
            System.out.println(cards.deck[i].toString());
        }
    }
}