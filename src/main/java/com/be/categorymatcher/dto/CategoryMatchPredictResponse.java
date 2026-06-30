package com.be.categorymatcher.dto;

import java.util.List;

public record CategoryMatchPredictResponse(
        List<CategoryMatchPrediction> results
) {
}
