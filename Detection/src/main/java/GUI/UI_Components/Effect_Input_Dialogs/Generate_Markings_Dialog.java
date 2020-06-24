package GUI.UI_Components.Effect_Input_Dialogs;

import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import ProjectManager.ProjectStatics;
import Effects.IEffect;
import Effects.SampleClassifier.MarkerFileGenerator;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;

public class Generate_Markings_Dialog implements Effect_UI_Component
{
    @FXML
    private Label lbl_damage_threshold, lbl_spike_threshold;
    @FXML
    private Slider sld_damage_threshold, sld_spike_threshold;
    @FXML
    private Button btn_apply, btn_cancel;

    private Pane main_layout;
    private Stage onTop = new Stage();
    private DataSourceException close_exception = null;

    private MarkerFileGenerator effect = null;

    @Override
    public void show( Window parent ) throws DataSourceException
    {
        FXMLLoader l = new FXMLLoader();
        l.setController( this );
        l.setLocation( ProjectStatics.getFxml_files_path( "generate_marks_UI.fxml" ) );
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
        lbl_damage_threshold.setText( String.format( "%.2f", sld_damage_threshold.getValue() ) );
        sld_damage_threshold.setOnMouseDragged( ev ->
                                         {
                                             lbl_damage_threshold.setText( String.format( "%.2f", sld_damage_threshold.getValue() ) );
                                         } );

        lbl_spike_threshold.setText( String.format( "%.2f", sld_spike_threshold.getValue() ) );
        sld_spike_threshold.setOnMouseDragged( ev ->
                                                {
                                                    lbl_spike_threshold.setText( String.format( "%.2f", sld_spike_threshold.getValue() ) );
                                                } );

        btn_apply.setOnAction( ev ->
                               {
                                   try
                                   {
                                       effect = new MarkerFileGenerator();
                                       effect.setMoving_avg_size( 8 );
                                       effect.setGen_mvg_avg( false );
                                       effect.setGen_marker( true );
                                       effect.setDest_path( null );
                                       effect.setAbs_threshold( ( float )sld_damage_threshold.getValue() );
                                       effect.setDuplicate_L_to_R( false );
                                       effect.setMin_marking_spacing( 1 );
                                       effect.setSide_extend( 2 );
                                       effect.setSpike_threshold( ( float )sld_spike_threshold.getValue() );

                                       effect.setGen_mvg_avg( true );
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

    public Generate_Markings_Dialog()
    {
    }

    @Override
    public DataSourceException get_close_exception()
    {
        return close_exception;
    }
}
