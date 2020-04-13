function rv = burg_predict( ys, pred_start, pred_end )
  forpred = ys( 1 : pred_start );
  order = size( forpred )( 2 ) - 3;
  la = arburg( forpred, order );
  for ii = pred_start : pred_end
     ys( ii ) = -sum( la( 2 : end ) .* ys( ( ii - 1 ) : -1 : ( ii - order ) ) );
  end
  rv = ys;
end
