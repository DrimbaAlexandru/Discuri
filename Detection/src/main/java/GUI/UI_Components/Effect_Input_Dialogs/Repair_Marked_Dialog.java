package GUI.UI_Components.Effect_Input_Dialogs;

import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import ProjectManager.ProjectStatics;
import Effects.IEffect;
import Effects.Multi_Band_Repair_Marked;
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
    private Label lbl_lp_coeffs, lbl_spike_thrsh, lbl_drst, lbl_max_repair_length;
    @FXML
    private CheckBox chk_16000_cut, chk_6000_cut, chk_2000_cut,chk_500_cut,chk_repair_residue, chk_use_direct_repair;
    @FXML
    private Slider slider_lp_coeffs, slider_spike_thrsh, slider_max_repair_length;
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
        l.setLocation( ProjectStatics.getFxml_files_path( "repair_UI.fxml" ) );
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
        lbl_spike_thrsh.setText( String.format( "%.1f", slider_spike_thrsh.getValue() ) );
        lbl_drst.setDisable( !chk_use_direct_repair.isSelected() );
        slider_spike_thrsh.setDisable( !chk_use_direct_repair.isSelected() );
        lbl_spike_thrsh.setDisable( !chk_use_direct_repair.isSelected() );
        lbl_max_repair_length.setText( String.format( "%d", ( int )slider_max_repair_length.getValue() ) );

        slider_max_repair_length.setOnMouseDragged( ev ->
                                                    {
                                                        lbl_max_repair_length.setText( String.format( "%d", ( int )slider_max_repair_length.getValue() ) );
                                                    } );

        slider_lp_coeffs.setOnMouseDragged( ev ->
                                      {
                                          lbl_lp_coeffs.setText( "" + ( int )slider_lp_coeffs.getValue() );
                                      } );
        slider_spike_thrsh.setOnMouseDragged( ev ->
                                              {
                                                  lbl_spike_thrsh.setText( String.format( "%.1f", slider_spike_thrsh.getValue() ) );
                                              } );
        chk_use_direct_repair.setOnAction( ev ->
                                           {
                                               lbl_drst.setDisable( !chk_use_direct_repair.isSelected() );
                                               slider_spike_thrsh.setDisable( !chk_use_direct_repair.isSelected() );
                                               lbl_spike_thrsh.setDisable( !chk_use_direct_repair.isSelected() );
                                           } );
        btn_apply.setOnAction( ev ->
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
                                       if( chk_16000_cut.isSelected() )
                                       {
                                           effect.getBand_cutoffs().add( 16000 );
                                       }
                                       effect.setRepair_residue( chk_repair_residue.isSelected() );
                                       effect.setCompare_with_direct_repair( chk_use_direct_repair.isSelected() );
                                       effect.setFetch_ratio( ( int )slider_lp_coeffs.getValue() );
                                       effect.setpeak_threshold( ( float )slider_spike_thrsh.getValue() );
                                       effect.setMax_repair_size( ( int )slider_max_repair_length.getValue() );
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

