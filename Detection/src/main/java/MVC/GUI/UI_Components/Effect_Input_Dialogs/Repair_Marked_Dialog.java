package MVC.GUI.UI_Components.Effect_Input_Dialogs;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import SignalProcessing.Effects.Equalizer;
import SignalProcessing.Effects.IEffect;
import SignalProcessing.Effects.Multi_Band_Repair_Marked;
import SignalProcessing.Filters.FIR;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;

/**
 * Created by Alex on 19.06.2018.
 */
public class Repair_Marked_Dialog implements Effect_UI_Component
{
    @FXML
    private Label lbl_lp_coeffs;
    @FXML
    private CheckBox chk_6000_cut, chk_2000_cut,chk_500_cut,chk_repair_residue, chk_use_direct_repair;
    @FXML
    private Slider slider_lp_coeffs;
    @FXML
    private Button btn_apply, btn_cancel;

    private Pane main_layout;
    private Stage onTop = new Stage();
    private DataSourceException close_exception = null;

    private Multi_Band_Repair_Marked effect = null;

    @Override
    public void show( Window parent ) throws DataSourceException
    {
        FXMLLoader l = new FXMLLoader();
        l.setController( this );
        l.setLocation( getClass().getClassLoader().getResource( "repair_UI.fxml" ) );
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

        lbl_lp_coeffs.setText( "" + ( int )slider_lp_coeffs.getValue() );
        slider_lp_coeffs.setOnMouseDragged( ev ->
                                      {
                                          lbl_lp_coeffs.setText( "" + ( int )slider_lp_coeffs.getValue() );
                                      } );
        btn_apply.setOnAction( ( ev ) ->
                               {
                                   try
                                   {
                                       effect = new Multi_Band_Repair_Marked();
                                       if( chk_500_cut.isSelected() )
                                       {
                                           effect.getBand_cutoffs().add( 500 );
                                       }
                                       if( chk_2000_cut.isSelected() )
                                       {
                                           effect.getBand_cutoffs().add( 2000 );
                                       }
                                       if( chk_6000_cut.isSelected() )
                                       {
                                           effect.getBand_cutoffs().add( 6000 );
                                       }
                                       effect.setRepair_residue( chk_repair_residue.isSelected() );
                                       effect.setCompare_with_direct_repair( chk_use_direct_repair.isSelected() );
                                       effect.setFetch_ratio( ( int )slider_lp_coeffs.getValue() );
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

    public Repair_Marked_Dialog()
    {
    }

    @Override
    public DataSourceException get_close_exception()
    {
        return close_exception;
    }
}

