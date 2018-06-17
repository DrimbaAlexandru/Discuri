package MVC.GUI.UI_Components;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import SignalProcessing.Effects.Equalizer;
import SignalProcessing.Effects.IEffect;
import SignalProcessing.Filters.FIR;
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

    private Equalizer effect = null;

    @Override
    public void show( Window parent )
    {
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

    public Amplify_Dialog() throws IOException, DataSourceException
    {
        FXMLLoader l = new FXMLLoader();
        l.setController( this );
        l.setLocation( getClass().getClassLoader().getResource( "amplify_UI.fxml" ) );
        main_layout = l.load();
        if( main_layout == null )
        {
            throw new IOException( "Failed to load FXML file" );
        }
        btn_apply.setOnAction( ( ev ) ->
                               {
                                   effect = new Equalizer();
                                   double amplification = 0;
                                   try
                                   {
                                       amplification = Double.parseDouble( txt_gain.getText() );
                                       amplification = Math.pow( 2, amplification / 6 );
                                       double[] fir = new double[]{ amplification };
                                       effect = new Equalizer();
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
    }

    @Override
    public DataSourceException get_close_exception()
    {
        return close_exception;
    }
}
