package GUI.UI_Components.Effect_Input_Dialogs;

import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import ProjectManager.ProjectStatics;
import Effects.FIR_Equalizer;
import Effects.IEffect;
import SignalProcessing.Filters.FIR;
import Utils.Util_Stuff;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;

/**
 * Created by Alex on 17.06.2018.
 */
public class Amplify_Dialog implements Effect_UI_Component
{
    @FXML
    private TextField txt_gain;
    @FXML
    private Button btn_apply, btn_cancel;

    private Pane main_layout;
    private Stage onTop = new Stage();
    private DataSourceException close_exception = null;

    private FIR_Equalizer effect = null;

    @Override
    public void show( Window parent ) throws DataSourceException
    {
        FXMLLoader l = new FXMLLoader();
        l.setController( this );
        l.setLocation( ProjectStatics.getFxml_files_path( "amplify_UI.fxml" ) );
        try
        {
            main_layout = l.load();
            if( main_layout == null )
            {
                throw new DataSourceException( "Failed to load FXML file", DataSourceExceptionCause.INVALID_STATE );
            }
        }
        catch( IOException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
        }
        btn_apply.setOnAction( ev ->
                               {
                                   effect = new FIR_Equalizer();
                                   float amplification = 0;
                                   try
                                   {
                                       amplification = Float.parseFloat( txt_gain.getText() );
                                       float[] fir = new float[]{ amplification };
                                       Util_Stuff.dB2lin( fir, 1 );
                                       effect = new FIR_Equalizer();
                                       effect.setFilter( new FIR( fir, 1 ) );
                                   }
                                   catch( Exception e )
                                   {
                                       close_exception = new DataSourceException( e.getMessage(), DataSourceExceptionCause.INVALID_USER_INPUT );
                                   }
                                   onTop.close();
                               } );
        btn_cancel.setOnAction( ( ev ) ->
                                {
                                    onTop.close();
                                } );

        onTop.initOwner( parent );
        onTop.initModality( Modality.WINDOW_MODAL );
        onTop.setResizable( false );
        onTop.setScene( new Scene( main_layout ) );
        onTop.showAndWait();
    }

    @Override
    public IEffect get_prepared_effect()
    {
        return effect;
    }

    public Amplify_Dialog()
    {
    }

    @Override
    public DataSourceException get_close_exception()
    {
        return close_exception;
    }
}
