package dmitry.polyakov.components;

import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

import java.util.ArrayList;
import java.util.List;

import static dmitry.polyakov.constants.BotMenuCommand.*;
import static dmitry.polyakov.components.LanguageLocalisation.messages;

/**
 * @author Dmitry Polyakov
 * @created 04.02.2023 16:05
 */
public class Menu {

    public static List<BotCommand> addBotCommands() {
        List<BotCommand> listOfCommands = new ArrayList<>();

        listOfCommands.add(new BotCommand(START,
                messages.getString("menu_start")));
        listOfCommands.add(new BotCommand(MY_DATA,
                messages.getString("menu_show")));
        listOfCommands.add(new BotCommand(DELETE,
                messages.getString("menu_delete")));
        listOfCommands.add(new BotCommand(HELP,
                messages.getString("menu_help")));
        listOfCommands.add(new BotCommand(WEATHER,
                messages.getString("menu_weather")));
        listOfCommands.add(new BotCommand(DICTIONARY,
                messages.getString("menu_dictionary")));
        listOfCommands.add(new BotCommand(SETTINGS,
                messages.getString("menu_settings")));
        listOfCommands.add(new BotCommand(LANGUAGE,
                messages.getString("menu_language")));
        listOfCommands.add(new BotCommand(REGISTER,
                messages.getString("menu_register")));

        return listOfCommands;
    }
}
