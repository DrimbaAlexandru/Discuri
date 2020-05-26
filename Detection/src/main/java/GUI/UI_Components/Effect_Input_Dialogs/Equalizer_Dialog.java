package GUI.UI_Components.Effect_Input_Dialogs;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import ProjectManager.ProjectStatics;
import SignalProcessing.Effects.FFT_Equalizer;
import SignalProcessing.Effects.FIR_Equalizer;
import SignalProcessing.Effects.IEffect;
import SignalProcessing.Filters.FIR;
import Utils.MyPair;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.HashMap;

/**
 * Created by Alex on 20.06.2018.
 */
public class Equalizer_Dialog implements Effect_UI_Component
{
    @FXML
    private ChoiceBox< String > ch_eq_curve;
    @FXML
    private Slider sld_fil_len;
    @FXML
    private Label lbl_fil_len;
    @FXML
    private Button btn_apply, btn_cancel;

    private Pane main_layout;
    private Stage onTop = new Stage();
    private DataSourceException close_exception = null;
    private int sample_rate;
    private int filter_length;

    private FFT_Equalizer effect = null;

    private final HashMap< String, MyPair< float[], float[] > > equalization_curves = new HashMap<>();

    @Override
    public void show( Window parent ) throws DataSourceException
    {
        FXMLLoader l = new FXMLLoader();
        l.setController( this );
        l.setLocation( ProjectStatics.getFxml_files_path( "eq_UI.fxml" ) );
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
        ch_eq_curve.getItems().clear();
        ch_eq_curve.getItems().addAll( equalization_curves.keySet() );

        filter_length = ( ( int )sld_fil_len.getValue() - 1 ) / 2 * 2 + 1;
        sld_fil_len.setValue( filter_length );
        lbl_fil_len.setText( filter_length + "" );

        sld_fil_len.setOnMouseDragged( ev ->
                                       {
                                           filter_length = ( ( int )sld_fil_len.getValue() - 1 ) / 2 * 2 + 1;
                                           sld_fil_len.setValue( filter_length );
                                           lbl_fil_len.setText( filter_length + "" );
                                       } );

        btn_apply.setOnAction( ev ->
                               {
                                   effect = new FFT_Equalizer();
                                   try
                                   {
                                       if( ch_eq_curve.getValue() == null )
                                       {
                                           throw new DataSourceException( "No curve was selected", DataSourceExceptionCause.INVALID_USER_INPUT );
                                       }
                                       MyPair< float[], float[] > resp = equalization_curves.get( ch_eq_curve.getValue() );
                                       FIR fir = FIR.fromFreqResponse( resp.getLeft(), resp.getRight(), resp.getLeft().length, sample_rate, filter_length );
                                       effect = new FFT_Equalizer();
                                       effect.setFilter( fir );
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

    public Equalizer_Dialog( int project_sample_rate )
    {
        sample_rate = project_sample_rate;
        filter_length = 511;
        equalization_curves.put( "RIAA curve", FIR.get_RIAA_response() );
        equalization_curves.put( "Inverse RIAA curve", FIR.get_inverse_RIAA_response() );
        equalization_curves.put( "Flat Impulse", FIR.get_flat_response() );
    }

    @Override
    public DataSourceException get_close_exception()
    {
        return close_exception;
    }
}

