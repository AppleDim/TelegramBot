package dmitry.polyakov.components;

import dmitry.polyakov.service.LanguageLocalisation;
import dmitry.polyakov.service.TelegramBot;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * @author Dmitry Polyakov
 * @created 04.02.2023 16:05
 **/
public class MenuCommands {

    public static List<BotCommand> addBotCommands() {
        List<BotCommand> listOfCommands = new ArrayList<>();

        ResourceBundle messages = TelegramBot.messages;

        listOfCommands.add(new BotCommand("/start",
                messages.getString("menu_start")));
        listOfCommands.add(new BotCommand("/mydata",
                messages.getString("menu_show")));
        listOfCommands.add(new BotCommand("/delete",
                messages.getString("menu_delete")));
        listOfCommands.add(new BotCommand("/help",
                messages.getString("menu_help")));
        listOfCommands.add(new BotCommand("/weather",
                messages.getString("menu_weather")));
        listOfCommands.add(new BotCommand("/sentences",
                messages.getString("menu_sentences")));
        listOfCommands.add(new BotCommand("/settings",
                messages.getString("menu_settings")));
        listOfCommands.add(new BotCommand("/language",
                messages.getString("menu_language")));
        listOfCommands.add(new BotCommand("/register",
                messages.getString("menu_register")));

        return listOfCommands;
    }
}
