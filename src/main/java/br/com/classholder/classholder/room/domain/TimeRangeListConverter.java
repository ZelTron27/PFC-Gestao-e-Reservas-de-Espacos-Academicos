package br.com.classholder.classholder.room.domain;

import java.util.List;

import br.com.classholder.classholder.room.dto.TimeRange;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TimeRangeListConverter implements AttributeConverter<List<TimeRange>, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<TimeRange>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<TimeRange> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (JacksonException e) {
            throw new IllegalStateException("Erro ao salvar os horários de funcionamento da sala", e);
        }
    }

    @Override
    public List<TimeRange> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (JacksonException e) {
            throw new IllegalStateException("Erro ao ler os horários de funcionamento da sala", e);
        }
    }

}
