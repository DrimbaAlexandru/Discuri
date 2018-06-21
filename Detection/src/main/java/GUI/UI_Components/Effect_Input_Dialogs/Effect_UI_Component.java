package GUI.UI_Components.Effect_Input_Dialogs;

import Exceptions.DataSourceException;
import SignalProcessing.Effects.IEffect;
import javafx.stage.Window;

/**
 * Created by Alex on 17.06.2018.
 */
public interface Effect_UI_Component
{
    void show( Window parent ) throws DataSourceException;

    IEffect get_prepared_effect();

    DataSourceException get_close_exception();
}

