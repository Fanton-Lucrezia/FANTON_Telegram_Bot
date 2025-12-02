package org.example;

import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.tutorial.Lesson1.src.MyBot;

public class Main {
    public static void main(String[] args) {
        MyConfiguration myConfiguration = MyConfiguration.getInstance();
        //System.out.println("API key: " + myConfiguration.getProperty("API_KEY"));

        String botToken = myConfiguration.getProperty("BOT_TOKEN");
        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, new MyBot());
            System.out.println("MyBot successfully started!");
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}