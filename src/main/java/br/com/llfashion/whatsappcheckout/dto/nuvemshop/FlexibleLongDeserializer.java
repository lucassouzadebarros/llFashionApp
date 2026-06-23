package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;
import org.springframework.util.StringUtils;

public class FlexibleLongDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return parser.getLongValue();
        }
        if (token == JsonToken.VALUE_STRING) {
            String value = parser.getText();
            if (!StringUtils.hasText(value)) {
                return null;
            }
            try {
                return Long.valueOf(value.trim());
            } catch (NumberFormatException exception) {
                throw JsonMappingException.from(parser, "user_id não é um número válido: " + value, exception);
            }
        }
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        return (Long) context.handleUnexpectedToken(Long.class, parser);
    }
}
