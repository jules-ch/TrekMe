package com.peterlaurence.trekme.core.providers.urltilebuilder

class UrlTileBuilderOrdnanceSurvey: UrlTileBuilder {
    override fun build(level: Int, row: Int, col: Int): String {
        return "https://api.os.uk/maps/raster/v1/wmts?key=<key>&service=WMTS&version=1.0.0&request=GetTile&layer=Light_3857&style=Light&format=image/png&tileMatrixSet=EPSG:3857&tileMatrix=$level&tileRow=$row&tileCol=$col"
    }
}